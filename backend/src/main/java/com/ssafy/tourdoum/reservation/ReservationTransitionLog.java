package com.ssafy.tourdoum.reservation;

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
 * 예약 상태 전이 append-only audit. ADR-0013 §결정 (3.2) — 전자상거래법 시행령 §6 5년 보존.
 *
 * <p>본 엔티티는 INSERT-only로 운영해야 하며 UPDATE/DELETE는 금지.
 */
@Entity
@Table(name = "reservation_transition_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationTransitionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reservation_id", nullable = false)
  private Long reservationId;

  /** 최초 INSERT 시 NULL 허용 (예: QUEUED로 박을 때). */
  @Enumerated(EnumType.STRING)
  @Column(name = "from_state", length = 32)
  private ReservationState fromState;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_state", nullable = false, length = 32)
  private ReservationState toState;

  /** JSON 메타데이터 (PG response, idempotencyKey 등). 학습 단계 단순 String. */
  @Column(columnDefinition = "JSON")
  private String metadata;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public ReservationTransitionLog(
      Long reservationId,
      ReservationState fromState,
      ReservationState toState,
      String metadata) {
    this.reservationId = reservationId;
    this.fromState = fromState;
    this.toState = toState;
    this.metadata = metadata;
  }
}
