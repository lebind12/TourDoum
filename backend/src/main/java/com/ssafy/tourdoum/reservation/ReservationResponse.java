package com.ssafy.tourdoum.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 예약 응답 DTO. */
public record ReservationResponse(
    Long id,
    Long memberId,
    Long accommodationId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guests,
    int totalPrice,
    PaymentMethod paymentMethod,
    ReservationStatus status,
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
        reservation.getCreatedAt());
  }
}
