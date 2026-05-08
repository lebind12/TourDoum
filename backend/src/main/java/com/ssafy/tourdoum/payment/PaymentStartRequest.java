package com.ssafy.tourdoum.payment;

import jakarta.validation.constraints.NotNull;

/** PG 결제 시작 요청 — `POST /api/payments/start`. */
public record PaymentStartRequest(
    @NotNull(message = "reservationId 필수") Long reservationId) {}
