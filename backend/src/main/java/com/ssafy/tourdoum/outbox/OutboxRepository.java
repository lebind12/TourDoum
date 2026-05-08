package com.ssafy.tourdoum.outbox;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

  List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(Long aggregateId);

  List<OutboxEvent> findByAggregateIdAndEventType(Long aggregateId, String eventType);

  /**
   * Claim 후보 batch 조회 — ADR-0013 §결정 (11.1) FOR UPDATE SKIP LOCKED + LIMIT.
   *
   * <p>호출자는 트랜잭션 안에서 본 메서드 → {@link #markClaimed} 까지 한 단위로 묶어야 한다. SKIP LOCKED 덕분에
   * 다중 replica가 동시에 claim batch를 가져가도 row 중복 없음.
   *
   * <p>{@code claim_state='PENDING'} + `available_at <= :now` 조건으로 backoff 대기/지연 발행 row 제외.
   */
  @Query(
      value =
          "SELECT * FROM outbox "
              + " WHERE claim_state = 'PENDING' AND available_at <= :now "
              + " ORDER BY id "
              + " LIMIT :limit "
              + " FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> claimBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OutboxEvent o "
          + "   SET o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.CLAIMED, "
          + "       o.claimedBy = :claimedBy, "
          + "       o.claimedAt = :claimedAt "
          + " WHERE o.id = :id "
          + "   AND o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.PENDING")
  int markClaimed(
      @Param("id") Long id,
      @Param("claimedBy") String claimedBy,
      @Param("claimedAt") LocalDateTime claimedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OutboxEvent o "
          + "   SET o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.DONE, "
          + "       o.publishedAt = :publishedAt "
          + " WHERE o.id = :id")
  int markDone(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);

  /**
   * 실패 + backoff — claim_state를 다시 PENDING으로 풀고 attempt_count++ + available_at 연기.
   * publisher가 다음 tick에 재시도.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OutboxEvent o "
          + "   SET o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.PENDING, "
          + "       o.claimedBy = NULL, "
          + "       o.claimedAt = NULL, "
          + "       o.attemptCount = o.attemptCount + 1, "
          + "       o.availableAt = :nextAvailableAt "
          + " WHERE o.id = :id")
  int markBackoff(@Param("id") Long id, @Param("nextAvailableAt") LocalDateTime nextAvailableAt);

  /** terminal 실패 — dead_letter 이관 후 호출. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OutboxEvent o "
          + "   SET o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.FAILED "
          + " WHERE o.id = :id")
  int markFailedTerminal(@Param("id") Long id);

  /**
   * Stale claim recovery — TTL(기본 30s) 초과한 CLAIMED row를 PENDING으로 복원.
   * 워커가 claim 직후 죽은 경우 차회 tick에서 재처리.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OutboxEvent o "
          + "   SET o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.PENDING, "
          + "       o.claimedBy = NULL, "
          + "       o.claimedAt = NULL "
          + " WHERE o.claimState = com.ssafy.tourdoum.outbox.OutboxClaimState.CLAIMED "
          + "   AND o.claimedAt < :threshold")
  int recoverStaleClaims(@Param("threshold") LocalDateTime threshold);
}
