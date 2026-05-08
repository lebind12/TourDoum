package com.ssafy.tourdoum.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예약 응답 DTO.
 *
 * <p>BE-14: ADR-0013 §결정 (3) FSM SOT — `state` 필드 노출. 기존 `status`(legacy 3-state) 도 유지하여
 * 점진 이행 호환. ui R11 `USER_STATE_LABEL`은 `state` 필드를 우선 사용. legacy `status`는 별 task에서
 * deprecation 후 제거 예정.
 */
public record ReservationResponse(
    Long id,
    Long memberId,
    Long accommodationId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guests,
    int totalPrice,
    PaymentMethod paymentMethod,
    // BE-13 FSM 도입 후 SOT는 state. status는 legacy 호환용 (별 task에서 deprecate).
    ReservationStatus status,
    // ADR-0013 FSM SOT — 13 enum.
    ReservationState state,
    LocalDateTime createdAt) {

  public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        reservation.getMemberId(),
        reservation.getAccommodationId(),
        reservation.getCheckIn(),
        reservation.getCheckOut(),
        reservation.getGuests(),
        reservation.getTotalPrice(),
        reservation.getPaymentMethod(),
        reservation.getStatus(),
        reservation.getState(),
        reservation.getCreatedAt());
  }
}
