package com.ssafy.tourdoum.payment;

import com.ssafy.tourdoum.reservation.ReservationState;

/** ui R11 `PaymentStartData` 1:1 동치. */
public record PaymentStartData(
    String reservationId, ReservationState state, TossStatus tossStatus, String redirectTo) {}
