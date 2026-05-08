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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 예약 엔티티.
 *
 * <p>idempotencyKey: Idempotency-Key 헤더 값 → UNIQUE INDEX로 중복 confirm 방지.
 */
@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "accommodation_id", nullable = false)
  private Long accommodationId;

  @Column(name = "check_in", nullable = false)
  private LocalDate checkIn;

  @Column(name = "check_out", nullable = false)
  private LocalDate checkOut;

  @Column(nullable = false)
  private int guests;

  @Column(name = "total_price", nullable = false)
  private int totalPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private PaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationStatus status;

  /**
   * FSM SOT — ADR-0013 §결정 (3). 기존 {@link #status}와 V18~ 단계에서 병존하며 BE-14에서 통합.
   *
   * <p>본 column은 {@link ReservationRepository#transitionState} conditional UPDATE로만 갱신해야
   * replica 안전 + 멱등성이 보장된다.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReservationState state;

  /** Idempotency-Key 헤더 값 — UNIQUE INDEX로 중복 예약 방지. */
  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  private String idempotencyKey;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @org.springframework.data.annotation.LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public Reservation(
      Long memberId,
      Long accommodationId,
      LocalDate checkIn,
      LocalDate checkOut,
      int guests,
      int totalPrice,
      PaymentMethod paymentMethod,
      String idempotencyKey,
      ReservationState initialState) {
    this.memberId = memberId;
    this.accommodationId = accommodationId;
    this.checkIn = checkIn;
    this.checkOut = checkOut;
    this.guests = guests;
    this.totalPrice = totalPrice;
    this.paymentMethod = paymentMethod;
    this.status = ReservationStatus.CONFIRMED;
    this.state = initialState != null ? initialState : ReservationState.CONFIRMED;
    this.idempotencyKey = idempotencyKey;
  }

  /** 예약 취소 — legacy {@link ReservationStatus}만 갱신. FSM 전이는 BE-14에서 통합. */
  public void cancel() {
    this.status = ReservationStatus.CANCELED;
  }
}
