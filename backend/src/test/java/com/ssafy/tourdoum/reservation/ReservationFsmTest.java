package com.ssafy.tourdoum.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FSM 전이 정의 + 보상 이벤트 매핑 검증 — ADR-0013 §결정 (3). */
class ReservationFsmTest {

  @Test
  @DisplayName("happy path: QUEUED → ADMITTED → INVENTORY_RESERVED → PAYMENT_PENDING → AUTHORIZED → CAPTURED → CONFIRMED")
  void happy_path_chain_allowed() {
    assertThat(ReservationFsm.allowedPrev(ReservationState.ADMITTED))
        .containsExactly(ReservationState.QUEUED);
    assertThat(ReservationFsm.allowedPrev(ReservationState.INVENTORY_RESERVED))
        .contains(ReservationState.ADMITTED);
    assertThat(ReservationFsm.allowedPrev(ReservationState.PAYMENT_PENDING))
        .containsExactly(ReservationState.INVENTORY_RESERVED);
    assertThat(ReservationFsm.allowedPrev(ReservationState.AUTHORIZED))
        .containsExactly(ReservationState.PAYMENT_PENDING);
    assertThat(ReservationFsm.allowedPrev(ReservationState.CAPTURED))
        .containsExactly(ReservationState.AUTHORIZED);
    assertThat(ReservationFsm.allowedPrev(ReservationState.CONFIRMED))
        .containsExactly(ReservationState.CAPTURED);
  }

  @Test
  @DisplayName("refund 경로: CONFIRMED → REFUND_PENDING → REFUNDED")
  void refund_chain() {
    assertThat(ReservationFsm.allowedPrev(ReservationState.REFUND_PENDING))
        .containsExactly(ReservationState.CONFIRMED);
    assertThat(ReservationFsm.allowedPrev(ReservationState.REFUNDED))
        .containsExactly(ReservationState.REFUND_PENDING);
  }

  @Test
  @DisplayName("FAILED는 PAYMENT_PENDING/AUTHORIZED/INVENTORY_RESERVED 어디서든 진입 가능")
  void failed_multiple_sources() {
    assertThat(ReservationFsm.allowedPrev(ReservationState.FAILED))
        .containsExactlyInAnyOrder(
            ReservationState.PAYMENT_PENDING,
            ReservationState.AUTHORIZED,
            ReservationState.INVENTORY_RESERVED);
  }

  @Test
  @DisplayName("QUEUED는 시작 상태 — allowedPrev empty")
  void queued_is_start_state() {
    assertThat(ReservationFsm.allowedPrev(ReservationState.QUEUED)).isEmpty();
  }

  @Test
  @DisplayName("보상/알림 outbox 매핑")
  void compensation_outbox_mapping() {
    assertThat(ReservationFsm.emittedEventType(ReservationState.INVENTORY_RESERVED))
        .contains("PaymentRequested");
    assertThat(ReservationFsm.emittedEventType(ReservationState.CONFIRMED)).contains("Notify");
    assertThat(ReservationFsm.emittedEventType(ReservationState.FAILED))
        .contains("InventoryRelease");
    assertThat(ReservationFsm.emittedEventType(ReservationState.CANCELLED))
        .contains("InventoryRelease");
    assertThat(ReservationFsm.emittedEventType(ReservationState.REFUND_PENDING))
        .contains("RefundScheduled");
    // 매핑 없는 상태
    assertThat(ReservationFsm.emittedEventType(ReservationState.AUTHORIZED)).isEmpty();
    assertThat(ReservationFsm.emittedEventType(ReservationState.CAPTURED)).isEmpty();
    assertThat(ReservationFsm.emittedEventType(ReservationState.REFUNDED)).isEmpty();
  }
}
