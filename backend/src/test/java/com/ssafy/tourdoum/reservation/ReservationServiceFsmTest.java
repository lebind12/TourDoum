package com.ssafy.tourdoum.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.tourdoum.accommodation.Accommodation;
import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import com.ssafy.tourdoum.accommodation.AccommodationType;
import com.ssafy.tourdoum.notification.NotificationService;
import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BE-13 ReservationService FSM 단위 테스트 — reserve / transitionTo (멱등 + 보상 outbox). */
@ExtendWith(MockitoExtension.class)
class ReservationServiceFsmTest {

  @Mock private ReservationRepository reservationRepository;
  @Mock private AccommodationRepository accommodationRepository;
  @Mock private NotificationService notificationService;
  @Mock private ReservationTransitionLogRepository transitionLogRepository;
  @Mock private OutboxRepository outboxRepository;

  @InjectMocks private ReservationService service;

  private Accommodation accommodation() {
    return Accommodation.builder()
        .name("ho")
        .type(AccommodationType.HOTEL)
        .address("addr")
        .lat(new BigDecimal("37.5"))
        .lng(new BigDecimal("127.0"))
        .priceFrom(50_000)
        .build();
  }

  @Test
  @DisplayName("reserve — INVENTORY_RESERVED state로 INSERT + transition_log + outbox PaymentRequested")
  void reserve_inserts_with_outbox_paymentRequested() {
    given(accommodationRepository.findById(7L)).willReturn(Optional.of(accommodation()));
    given(reservationRepository.findByIdempotencyKey("k-1")).willReturn(Optional.empty());
    given(reservationRepository.save(any(Reservation.class)))
        .willAnswer(inv -> inv.getArgument(0));

    service.reserve(11L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 2, "k-1");

    ArgumentCaptor<Reservation> rc = ArgumentCaptor.forClass(Reservation.class);
    verify(reservationRepository).save(rc.capture());
    assertThat(rc.getValue().getState()).isEqualTo(ReservationState.INVENTORY_RESERVED);
    assertThat(rc.getValue().getTotalPrice()).isEqualTo(50_000 * 2 + 20_000);

    ArgumentCaptor<ReservationTransitionLog> lc =
        ArgumentCaptor.forClass(ReservationTransitionLog.class);
    verify(transitionLogRepository).save(lc.capture());
    assertThat(lc.getValue().getFromState()).isNull();
    assertThat(lc.getValue().getToState()).isEqualTo(ReservationState.INVENTORY_RESERVED);

    ArgumentCaptor<OutboxEvent> oc = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(oc.capture());
    assertThat(oc.getValue().getEventType()).isEqualTo("PaymentRequested");
  }

  @Test
  @DisplayName("reserve — 동일 idempotencyKey 재호출 시 기존 row 반환 (멱등)")
  void reserve_idempotent_on_existing_key() {
    Reservation existing =
        Reservation.builder()
            .memberId(1L)
            .accommodationId(7L)
            .checkIn(LocalDate.of(2026, 6, 1))
            .checkOut(LocalDate.of(2026, 6, 3))
            .guests(2)
            .totalPrice(120_000)
            .paymentMethod(PaymentMethod.CARD)
            .idempotencyKey("k-2")
            .initialState(ReservationState.INVENTORY_RESERVED)
            .build();
    given(reservationRepository.findByIdempotencyKey("k-2")).willReturn(Optional.of(existing));

    Reservation r =
        service.reserve(1L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 2, "k-2");

    assertThat(r).isSameAs(existing);
    verify(reservationRepository, never()).save(any());
    verify(transitionLogRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  @DisplayName("transitionTo — conditional UPDATE 1 row → log + 보상 outbox 발행")
  void transitionTo_success_emits_outbox() {
    Long rid = 99L;
    given(reservationRepository.transitionState(eq(rid), eq(ReservationState.CONFIRMED), anyCollection(), any()))
        .willReturn(1);
    Reservation stub =
        Reservation.builder()
            .memberId(1L)
            .accommodationId(7L)
            .checkIn(LocalDate.of(2026, 6, 1))
            .checkOut(LocalDate.of(2026, 6, 3))
            .guests(1)
            .totalPrice(70_000)
            .paymentMethod(PaymentMethod.CARD)
            .idempotencyKey("k-3")
            .initialState(ReservationState.CAPTURED)
            .build();
    given(reservationRepository.findById(rid)).willReturn(Optional.of(stub));

    boolean result = service.transitionTo(rid, ReservationState.CONFIRMED);

    assertThat(result).isTrue();
    verify(transitionLogRepository).save(any(ReservationTransitionLog.class));
    ArgumentCaptor<OutboxEvent> oc = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(oc.capture());
    assertThat(oc.getValue().getEventType()).isEqualTo("Notify");
  }

  @Test
  @DisplayName("transitionTo — 0 row 갱신 (다른 replica가 처리) 시 멱등 no-op + log/outbox 미발행")
  void transitionTo_idempotent_noop_on_zero_rows() {
    given(reservationRepository.transitionState(anyLong(), any(), anyCollection(), any()))
        .willReturn(0);

    boolean result = service.transitionTo(42L, ReservationState.AUTHORIZED);

    assertThat(result).isFalse();
    verify(transitionLogRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  @DisplayName("transitionTo — QUEUED는 시작 상태이므로 IllegalArgumentException")
  void transitionTo_queued_rejected() {
    assertThatThrownBy(() -> service.transitionTo(1L, ReservationState.QUEUED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("transitionTo — REFUND_PENDING 시 RefundScheduled outbox는 +3분 지연 availableAt")
  void transitionTo_refund_pending_schedules_with_delay() {
    Clock fixed = Clock.fixed(java.time.Instant.parse("2026-06-01T10:00:00Z"), java.time.ZoneOffset.UTC);
    ReservationService svc =
        new ReservationService(
            reservationRepository,
            accommodationRepository,
            notificationService,
            transitionLogRepository,
            outboxRepository,
            fixed);

    given(reservationRepository.transitionState(anyLong(), any(), anyCollection(), any()))
        .willReturn(1);
    given(reservationRepository.findById(7L))
        .willReturn(
            Optional.of(
                Reservation.builder()
                    .memberId(1L)
                    .accommodationId(2L)
                    .checkIn(LocalDate.of(2026, 6, 1))
                    .checkOut(LocalDate.of(2026, 6, 3))
                    .guests(1)
                    .totalPrice(70_000)
                    .paymentMethod(PaymentMethod.CARD)
                    .idempotencyKey("k-4")
                    .initialState(ReservationState.CONFIRMED)
                    .build()));

    svc.transitionTo(7L, ReservationState.REFUND_PENDING);

    ArgumentCaptor<OutboxEvent> oc = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(oc.capture());
    assertThat(oc.getValue().getEventType()).isEqualTo("RefundScheduled");
    LocalDateTime expectedAvailableAt =
        LocalDateTime.now(fixed).plus(java.time.Duration.ofMinutes(3));
    assertThat(oc.getValue().getAvailableAt()).isEqualTo(expectedAvailableAt);
  }
}
