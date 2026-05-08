package com.ssafy.tourdoum.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * Poison event 격리 — ADR-0013 §결정 (11.5). Admin UI {@code /admin/outbox}에서 수동 재시도.
 *
 * <p>BE-13 본 task는 ingest 경로 미구현 — BE-15 publisher worker에서 attempt_count 초과 시 INSERT.
 */
@Entity
@Table(name = "outbox_dead_letter")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxDeadLetter {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "original_id", nullable = false)
  private Long originalId;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(columnDefinition = "JSON")
  private String payload;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @CreatedDate
  @Column(name = "moved_at", nullable = false, updatable = false)
  private LocalDateTime movedAt;

  @Builder
  public OutboxDeadLetter(
      Long originalId,
      Long aggregateId,
      String eventType,
      String payload,
      String failureReason,
      int attemptCount) {
    this.originalId = originalId;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.failureReason = failureReason;
    this.attemptCount = attemptCount;
  }
}
