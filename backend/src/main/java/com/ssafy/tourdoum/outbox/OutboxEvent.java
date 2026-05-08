package com.ssafy.tourdoum.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Transactional outbox row — ADR-0013 §결정 (3.3) + §결정 (11).
 *
 * <p>{@link com.ssafy.tourdoum.reservation.ReservationService}의 도메인 트랜잭션 안에서 INSERT 되어,
 * 별도 publisher worker(BE-15)가 {@link OutboxClaimState#PENDING}을 claim → 외부 발행 → DONE.
 *
 * <p>본 task(BE-13)는 publisher 없이 INSERT만. PENDING row가 누적되어도 운영 영향 없음(저장만).
 */
@Entity
@Table(name = "outbox")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  /** payload JSON (Jackson String). 학습 단계 단순화 — schema는 BE-15에서 박제. */
  @Column(columnDefinition = "JSON")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "claim_state", nullable = false, length = 16)
  private OutboxClaimState claimState;

  @Column(name = "claimed_by", length = 64)
  private String claimedBy;

  @Column(name = "claimed_at")
  private LocalDateTime claimedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  /** 지연 발행 due (RefundScheduled 등). default = NOW(). */
  @Column(name = "available_at", nullable = false)
  private LocalDateTime availableAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Builder
  public OutboxEvent(
      Long aggregateId,
      String eventType,
      String payload,
      LocalDateTime availableAt) {
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.claimState = OutboxClaimState.PENDING;
    this.attemptCount = 0;
    this.availableAt = availableAt != null ? availableAt : LocalDateTime.now();
  }
}
