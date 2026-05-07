package com.ssafy.tourdoum.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 예약 확정 요청 DTO (step 3). */
public record ReservationConfirmRequest(
    @NotNull(message = "accommodationId는 필수입니다.") Long accommodationId,
    @NotNull(message = "checkIn은 필수입니다.") LocalDate checkIn,
    @NotNull(message = "checkOut은 필수입니다.") LocalDate checkOut,
    @Min(value = 1, message = "guests는 1 이상이어야 합니다.") int guests,
    @NotNull(message = "paymentMethod는 필수입니다.") PaymentMethod paymentMethod) {}
