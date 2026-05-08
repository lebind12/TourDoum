package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.outbox.OutboxClaimState;
import com.ssafy.tourdoum.outbox.OutboxDeadLetterRepository;
import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxEventDispatcher;
import com.ssafy.tourdoum.outbox.OutboxEventHandler;
import com.ssafy.tourdoum.outbox.OutboxPublisher;
import com.ssafy.tourdoum.outbox.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BE-15 OutboxPublisher IT — Testcontainers MySQL 8.4 + V18 적용.
 *
 * <p>검증:
 *
 * <ul>
 *   <li>PENDING row → drainOnce → DONE + handler 호출 1회
 *   <li>handler 5회 실패 → dead_letter 이관 + claim_state=FAILED
 *   <li>orphan eventType → 즉시 dead_letter
 *   <li>stale CLAIMED → recoverStale → PENDING 복원
 *   <li>available_at 미래 → claim 대상 외
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class OutboxPublisherIT {

  @SuppressWarnings("resource")
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tourdoum")
          .withUsername("tourdoum")
          .withPassword("tourdoum");

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/tourdoum?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    // 자동 스케줄러 발동 회피 — 테스트가 publisher 메서드 직접 호출.
    registry.add("tourdoum.outbox.tick-ms", () -> "3600000");
  }

  @Autowired private OutboxRepository outboxRepository;
  @Autowired private OutboxDeadLetterRepository deadLetterRepository;
  @Autowired private PlatformTransactionManager txManager;

  /** TX 안에서 INSERT 실행. */
  private OutboxEvent insert(String eventType, Long aggregateId, LocalDateTime availableAt) {
    return new TransactionTemplate(txManager)
        .execute(
            s ->
                outboxRepository.save(
                    OutboxEvent.builder()
                        .aggregateId(aggregateId)
                        .eventType(eventType)
                        .payload("{}")
                        .availableAt(availableAt)
                        .build()));
  }

  /** Test 한정 publisher — handler 인스턴스 + Clock 직접 주입. txManager는 autowired. */
  private OutboxPublisher buildPublisher(OutboxEventHandler... handlers) {
    TransactionTemplate tt = new TransactionTemplate(txManager);
    tt.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return new OutboxPublisher(
        outboxRepository,
        deadLetterRepository,
        new OutboxEventDispatcher(List.of(handlers)),
        tt,
        50,
        5,
        Duration.ofSeconds(30),
        Clock.systemDefaultZone());
  }

  @Test
  @DisplayName("PENDING 1건 → drainOnce → handler 호출 + DONE")
  void drains_pending_row() {
    OutboxEvent ev = insert("TestEvent", 1L, LocalDateTime.now());

    AtomicInteger calls = new AtomicInteger();
    OutboxEventHandler h =
        new OutboxEventHandler() {
          @Override public String eventType() { return "TestEvent"; }
          @Override public void handle(OutboxEvent e) { calls.incrementAndGet(); }
        };

    int processed = buildPublisher(h).drainOnce();

    assertThat(processed).isEqualTo(1);
    assertThat(calls.get()).isEqualTo(1);
    OutboxEvent reloaded = outboxRepository.findById(ev.getId()).orElseThrow();
    assertThat(reloaded.getClaimState()).isEqualTo(OutboxClaimState.DONE);
    assertThat(reloaded.getPublishedAt()).isNotNull();
  }

  @Test
  @DisplayName("orphan eventType → 즉시 dead_letter + FAILED")
  void orphan_event_to_dead_letter() {
    OutboxEvent ev = insert("UnknownEvent", 2L, LocalDateTime.now());

    long beforeDlq = deadLetterRepository.count();
    buildPublisher().drainOnce(); // handler 비어 있음 → orphan

    OutboxEvent reloaded = outboxRepository.findById(ev.getId()).orElseThrow();
    assertThat(reloaded.getClaimState()).isEqualTo(OutboxClaimState.FAILED);
    assertThat(deadLetterRepository.count()).isEqualTo(beforeDlq + 1);
  }

  @Test
  @DisplayName("max attempts 초과 → dead_letter 이관 + FAILED")
  void exhausts_attempts_to_dead_letter() {
    OutboxEvent ev = insert("FlakyEvent", 3L, LocalDateTime.now());

    OutboxEventHandler alwaysFail =
        new OutboxEventHandler() {
          @Override public String eventType() { return "FlakyEvent"; }
          @Override public void handle(OutboxEvent e) { throw new RuntimeException("nope"); }
        };

    OutboxPublisher publisher = buildPublisher(alwaysFail);
    long beforeDlq = deadLetterRepository.count();

    // 5회 시도 — publisher.processOne를 직접 호출 (backoff을 우회 시뮬).
    for (int i = 0; i < 5; i++) {
      publisher.processOne(ev.getId());
    }

    OutboxEvent reloaded = outboxRepository.findById(ev.getId()).orElseThrow();
    assertThat(reloaded.getClaimState()).isEqualTo(OutboxClaimState.FAILED);
    assertThat(deadLetterRepository.count()).isEqualTo(beforeDlq + 1);
  }

  @Test
  @DisplayName("stale CLAIMED → recoverStale → PENDING 복원")
  void stale_claim_recovered_to_pending() {
    OutboxEvent ev = insert("TestEvent", 4L, LocalDateTime.now());

    // 강제로 CLAIMED + 옛 claimed_at (TTL 초과) — markClaimed는 PENDING 매치 + 단발 UPDATE이므로 TX 필요.
    new TransactionTemplate(txManager)
        .execute(
            s ->
                outboxRepository.markClaimed(
                    ev.getId(), "old-worker", LocalDateTime.now().minusMinutes(5)));

    int recovered = buildPublisher().recoverStale();

    assertThat(recovered).isGreaterThanOrEqualTo(1);
    OutboxEvent reloaded = outboxRepository.findById(ev.getId()).orElseThrow();
    assertThat(reloaded.getClaimState()).isEqualTo(OutboxClaimState.PENDING);
    assertThat(reloaded.getClaimedBy()).isNull();
    assertThat(reloaded.getClaimedAt()).isNull();
  }

  @Test
  @DisplayName("available_at 미래 row는 claim 대상 외 (handler 호출 0)")
  void future_available_at_not_claimed() {
    // 고유 eventType으로 다른 테스트 영향 격리.
    OutboxEvent ev = insert("FutureOnlyEvent", 5L, LocalDateTime.now().plusMinutes(10));

    AtomicInteger calls = new AtomicInteger();
    OutboxEventHandler h =
        new OutboxEventHandler() {
          @Override public String eventType() { return "FutureOnlyEvent"; }
          @Override public void handle(OutboxEvent e) { calls.incrementAndGet(); }
        };

    buildPublisher(h).drainOnce();
    // 다른 테스트의 PENDING row가 잔존해 처리될 수 있으므로 handler 콜수만 검증.
    assertThat(calls.get()).as("미래 available_at row는 호출 안 됨").isZero();

    OutboxEvent reloaded = outboxRepository.findById(ev.getId()).orElseThrow();
    assertThat(reloaded.getClaimState()).isEqualTo(OutboxClaimState.PENDING);
  }
}
