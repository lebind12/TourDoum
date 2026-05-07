package com.ssafy.tourdoum.reservation;

import java.time.LocalDate;

/**
 * 예약 견적 응답 DTO.
 *
 * <p>step 1-2에서 실제 DB 저장 없이 가격만 계산해 반환한다.
 */
public record ReservationQuoteResponse(
    Long accommodationId,
    LocalDate checkIn,
    LocalDate checkOut,
    int nights,
    int guests,
    int pricePerNight,
    int cleaningFee,
    int totalPrice) {}
