package com.ssafy.tourdoum.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.tourdoum.reservation.PaymentMethod;
import com.ssafy.tourdoum.reservation.Reservation;
import com.ssafy.tourdoum.reservation.ReservationRepository;
import com.ssafy.tourdoum.reservation.ReservationService;
import com.ssafy.tourdoum.reservation.ReservationState;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PaymentMockService 단위 — happy / reject / timeout 시나리오 (Random seed 주입). */
class PaymentMockServiceTest {

  private final ReservationRepository reservationRepository =
      org.mockito.Mockito.mock(ReservationRepository.class);
  private final ReservationService reservationService =
      org.mockito.Mockito.mock(ReservationService.class);

  private Reservation stub(ReservationState state) {
    Reservation r =
        Reservation.builder()
            .memberId(1L)
            .accommodationId(2L)
            .checkIn(LocalDate.of(2026, 6, 1))
            .checkOut(LocalDate.of(2026, 6, 3))
            .guests(1)
            .totalPrice(80_000)
            .paymentMethod(PaymentMethod.CARD)
            .idempotencyKey("k-" + System.nanoTime())
            .initialState(state)
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 99L);
    return r;
  }

  @Test
  @DisplayName("happy path — INVENTORY_RESERVED → PAYMENT_PENDING → AUTHORIZED → CAPTURED → CONFIRMED, Toss DONE")
  void happy_path() {
    given(reservationRepository.findById(99L))
        .willReturn(Optional.of(stub(ReservationState.INVENTORY_RESERVED)));
    PaymentMockService svc =
        new PaymentMockService(reservationRepository, reservationService, 0.0, 0.0, new Random(42));

    PaymentStartData out = svc.start(99L);

    verify(reservationService).transitionTo(99L, ReservationState.PAYMENT_PENDING);
    verify(reservationService).transitionTo(99L, ReservationState.AUTHORIZED);
    verify(reservationService).transitionTo(99L, ReservationState.CAPTURED);
    verify(reservationService).transitionTo(99L, ReservationState.CONFIRMED);
    verify(reservationService, never()).transitionTo(eq(99L), eq(ReservationState.REJECTED));
    verify(reservationService, never()).transitionTo(eq(99L), eq(ReservationState.FAILED));
    assertThat(out.tossStatus()).isEqualTo(TossStatus.DONE);
    assertThat(out.state()).isEqualTo(ReservationState.CONFIRMED);
    assertThat(out.redirectTo()).contains("done");
  }

  @Test
  @DisplayName("100% reject — Toss ABORTED + state REJECTED")
  void all_reject() {
    given(reservationRepository.findById(99L))
        .willReturn(Optional.of(stub(ReservationState.INVENTORY_RESERVED)));
    PaymentMockService svc =
        new PaymentMockService(reservationRepository, reservationService, 1.0, 0.0, new Random(0));

    PaymentStartData out = svc.start(99L);

    assertThat(out.tossStatus()).isEqualTo(TossStatus.ABORTED);
    assertThat(out.state()).isEqualTo(ReservationState.REJECTED);
    verify(reservationService).transitionTo(99L, ReservationState.PAYMENT_PENDING);
    verify(reservationService).transitionTo(99L, ReservationState.REJECTED);
    verify(reservationService, never()).transitionTo(eq(99L), eq(ReservationState.AUTHORIZED));
  }

  @Test
  @DisplayName("100% timeout — Toss EXPIRED + state FAILED")
  void all_timeout() {
    given(reservationRepository.findById(99L))
        .willReturn(Optional.of(stub(ReservationState.INVENTORY_RESERVED)));
    PaymentMockService svc =
        new PaymentMockService(reservationRepository, reservationService, 0.0, 1.0, new Random(0));

    PaymentStartData out = svc.start(99L);

    assertThat(out.tossStatus()).isEqualTo(TossStatus.EXPIRED);
    assertThat(out.state()).isEqualTo(ReservationState.FAILED);
    verify(reservationService).transitionTo(99L, ReservationState.FAILED);
  }

  @Test
  @DisplayName("INVENTORY_RESERVED 외 상태 → IllegalStateException")
  void wrong_state_rejected() {
    given(reservationRepository.findById(99L))
        .willReturn(Optional.of(stub(ReservationState.CONFIRMED)));
    PaymentMockService svc =
        new PaymentMockService(reservationRepository, reservationService, 0.0, 0.0, new Random(0));

    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> svc.start(99L));
  }
}
