package com.ssafy.tourdoum.reservation;

/**
 * Reservation FSM 상태 — ADR-0013 §결정 (3).
 *
 * <p>본 enum은 {@code reservations.state} column의 SOT(단일 진실원). 기존 {@link ReservationStatus}
 * (PENDING/CONFIRMED/CANCELED)와 V18 마이그레이션 단계에선 병존하며, BE-14에서 통합 예정.
 *
 * <p>전이는 {@link ReservationFsm#allowedPrev(ReservationState)} 정의를 따른다. transition은
 * conditional UPDATE(`WHERE state = expectedPrev`)로 이루어져 멱등 + replica 안전.
 */
public enum ReservationState {
  QUEUED,
  ADMITTED,
  INVENTORY_RESERVED,
  PAYMENT_PENDING,
  AUTHORIZED,
  CAPTURED,
  CONFIRMED,
  REJECTED,
  CANCELLED,
  REVERSED,
  REFUND_PENDING,
  REFUNDED,
  FAILED
}
