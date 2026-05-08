package com.ssafy.tourdoum.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.reservation.ReservationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Toss → 내부 FSM 매핑 — ui R11 `TOSS_TO_INTERNAL` 와 1:1 동치 검증. */
class TossStatusMapperTest {

  @Test
  @DisplayName("READY/IN_PROGRESS → PAYMENT_PENDING")
  void mapping_pending() {
    assertThat(TossStatusMapper.toInternal(TossStatus.READY))
        .isEqualTo(ReservationState.PAYMENT_PENDING);
    assertThat(TossStatusMapper.toInternal(TossStatus.IN_PROGRESS))
        .isEqualTo(ReservationState.PAYMENT_PENDING);
  }

  @Test
  @DisplayName("DONE → CAPTURED")
  void mapping_done() {
    assertThat(TossStatusMapper.toInternal(TossStatus.DONE)).isEqualTo(ReservationState.CAPTURED);
  }

  @Test
  @DisplayName("ABORTED/EXPIRED → FAILED")
  void mapping_failed() {
    assertThat(TossStatusMapper.toInternal(TossStatus.ABORTED))
        .isEqualTo(ReservationState.FAILED);
    assertThat(TossStatusMapper.toInternal(TossStatus.EXPIRED))
        .isEqualTo(ReservationState.FAILED);
  }

  @Test
  @DisplayName("CANCELED → CANCELLED / PARTIAL_CANCELED → REFUND_PENDING")
  void mapping_cancel_refund() {
    assertThat(TossStatusMapper.toInternal(TossStatus.CANCELED))
        .isEqualTo(ReservationState.CANCELLED);
    assertThat(TossStatusMapper.toInternal(TossStatus.PARTIAL_CANCELED))
        .isEqualTo(ReservationState.REFUND_PENDING);
  }
}
