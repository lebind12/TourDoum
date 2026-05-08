package com.ssafy.tourdoum.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox publisher worker — ADR-0013 §결정 (11) 5요소 박제.
 *
 * <p>매 tick:
 *
 * <ol>
 *   <li>stale CLAIMED row(claimed_at &lt; now-TTL) → PENDING 복원 — 워커 사망 회복.
 *   <li>{@code SELECT … FOR UPDATE SKIP LOCKED LIMIT N} 으로 PENDING batch claim → claim_state=CLAIMED.
 *   <li>각 row를 별 트랜잭션({@code REQUIRES_NEW})에서 dispatcher.handle 호출.
 *   <li>성공: claim_state=DONE / 실패: attempt_count++ + exponential backoff(2^n s) /
 *       attempt &gt;= max: dead_letter INSERT + claim_state=FAILED.
 * </ol>
 *
 * <p>다중 replica 안전: SKIP LOCKED 덕분에 동시 claim batch가 겹치지 않음. Shedlock 없이도 작동.
 *
 * <p>{@link TransactionTemplate}을 명시적으로 사용 — {@code @Transactional} 자기-호출 함정을 회피하고
 * 트랜잭션 경계를 코드 레벨에 박제(테스트가 plain new 로 주입해도 동작 동일).
 */
@Component
public class OutboxPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);

  /** Worker id — replica 식별. JVM별 random UUID로 충분. */
  static final String WORKER_ID = shortHostId();

  private final OutboxRepository outboxRepository;
  private final OutboxDeadLetterRepository deadLetterRepository;
  private final OutboxEventDispatcher dispatcher;
  private final TransactionTemplate txTemplate;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration claimTtl;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public OutboxPublisher(
      OutboxRepository outboxRepository,
      OutboxDeadLetterRepository deadLetterRepository,
      OutboxEventDispatcher dispatcher,
      PlatformTransactionManager txManager,
      @Value("${tourdoum.outbox.batch-size:50}") int batchSize,
      @Value("${tourdoum.outbox.max-attempts:5}") int maxAttempts,
      @Value("${tourdoum.outbox.claim-ttl-seconds:30}") long claimTtlSeconds) {
    this(
        outboxRepository,
        deadLetterRepository,
        dispatcher,
        buildTemplate(txManager),
        batchSize,
        maxAttempts,
        Duration.ofSeconds(claimTtlSeconds),
        Clock.systemDefaultZone());
  }

  /** 테스트 친화 — Clock + duration + tx template 직접 주입. */
  public OutboxPublisher(
      OutboxRepository outboxRepository,
      OutboxDeadLetterRepository deadLetterRepository,
      OutboxEventDispatcher dispatcher,
      TransactionTemplate txTemplate,
      int batchSize,
      int maxAttempts,
      Duration claimTtl,
      Clock clock) {
    this.outboxRepository = outboxRepository;
    this.deadLetterRepository = deadLetterRepository;
    this.dispatcher = dispatcher;
    this.txTemplate = txTemplate;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.claimTtl = claimTtl;
    this.clock = clock != null ? clock : Clock.systemDefaultZone();
  }

  private static TransactionTemplate buildTemplate(PlatformTransactionManager txManager) {
    TransactionTemplate t = new TransactionTemplate(txManager);
    t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return t;
  }

  @Scheduled(fixedDelayString = "${tourdoum.outbox.tick-ms:500}")
  public void tick() {
    try {
      recoverStale();
      drainOnce();
    } catch (RuntimeException ex) {
      LOG.warn("[outbox-publisher] tick 실패 — 다음 tick에 재시도: {}", ex.getMessage());
    }
  }

  /** 한 tick에 처리되는 핵심 로직 — 테스트가 직접 호출. */
  public int drainOnce() {
    List<OutboxEvent> claimed = claimBatchTx();
    int processed = 0;
    for (OutboxEvent ev : claimed) {
      processOne(ev.getId());
      processed++;
    }
    return processed;
  }

  /** Stale claim 복원만 단독 호출 가능. */
  public int recoverStale() {
    return txTemplate.execute(
        s -> {
          LocalDateTime threshold = LocalDateTime.now(clock).minus(claimTtl);
          int n = outboxRepository.recoverStaleClaims(threshold);
          if (n > 0) {
            LOG.info("[outbox-publisher] stale CLAIMED → PENDING 복원: {} row", n);
          }
          return n;
        });
  }

  /** SKIP LOCKED + batch claim — 별 트랜잭션. */
  protected List<OutboxEvent> claimBatchTx() {
    return txTemplate.execute(
        s -> {
          LocalDateTime now = LocalDateTime.now(clock);
          List<OutboxEvent> candidates = outboxRepository.claimBatch(now, batchSize);
          for (OutboxEvent ev : candidates) {
            outboxRepository.markClaimed(ev.getId(), WORKER_ID, now);
          }
          return candidates;
        });
  }

  /** 단일 row dispatch — 본 메서드 자체가 별 트랜잭션. handler 실패 시 backoff/dead_letter 처리. */
  public void processOne(Long outboxId) {
    txTemplate.executeWithoutResult(
        s -> {
          OutboxEvent ev = outboxRepository.findById(outboxId).orElse(null);
          if (ev == null) {
            return; // 누군가 삭제 (정상 흐름엔 없음).
          }
          var handlerOpt = dispatcher.handlerFor(ev.getEventType());
          if (handlerOpt.isEmpty()) {
            // orphan event — 즉시 dead_letter 이관.
            moveToDeadLetter(ev, "no handler registered for eventType=" + ev.getEventType());
            return;
          }
          try {
            handlerOpt.get().handle(ev);
            outboxRepository.markDone(ev.getId(), LocalDateTime.now(clock));
          } catch (RuntimeException ex) {
            int nextAttempt = ev.getAttemptCount() + 1;
            if (nextAttempt >= maxAttempts) {
              moveToDeadLetter(
                  ev,
                  "max attempts exceeded ("
                      + nextAttempt
                      + "): "
                      + ex.getClass().getSimpleName()
                      + ": "
                      + ex.getMessage());
              return;
            }
            LocalDateTime nextAvailableAt =
                LocalDateTime.now(clock).plusSeconds(backoffSeconds(nextAttempt));
            outboxRepository.markBackoff(ev.getId(), nextAvailableAt);
            LOG.warn(
                "[outbox-publisher] handle 실패 — id={} eventType={} attempt={} → 재시도 at {}",
                ev.getId(),
                ev.getEventType(),
                nextAttempt,
                nextAvailableAt);
          }
        });
  }

  private void moveToDeadLetter(OutboxEvent ev, String failureReason) {
    deadLetterRepository.save(
        OutboxDeadLetter.builder()
            .originalId(ev.getId())
            .aggregateId(ev.getAggregateId())
            .eventType(ev.getEventType())
            .payload(ev.getPayload())
            .failureReason(failureReason)
            .attemptCount(ev.getAttemptCount() + 1)
            .build());
    outboxRepository.markFailedTerminal(ev.getId());
    LOG.error(
        "[outbox-publisher] dead_letter 이관 — id={} eventType={} reason={}",
        ev.getId(),
        ev.getEventType(),
        failureReason);
  }

  /** Exponential backoff: 2^attempt 초. attempt=1 → 2s, 2 → 4s, 3 → 8s, 4 → 16s, 5 → 32s. cap 2^10. */
  static long backoffSeconds(int attempt) {
    int safe = Math.max(1, Math.min(attempt, 10));
    return 1L << safe;
  }

  private static String shortHostId() {
    try {
      return java.net.InetAddress.getLocalHost().getHostName()
          + "/"
          + UUID.randomUUID().toString().substring(0, 8);
    } catch (Exception e) {
      return "unknown/" + UUID.randomUUID().toString().substring(0, 8);
    }
  }
}
