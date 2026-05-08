package com.ssafy.tourdoum.payment;

import com.ssafy.tourdoum.reservation.ReservationState;
import java.util.EnumMap;
import java.util.Map;

/**
 * Toss Payments status ↔ 내부 FSM 상태 매핑. ui R11 `TOSS_TO_INTERNAL` 표와 1:1 동치 박제.
 *
 * <pre>
 * READY            → PAYMENT_PENDING
 * IN_PROGRESS      → PAYMENT_PENDING
 * DONE             → CAPTURED
 * ABORTED          → FAILED
 * EXPIRED          → FAILED
 * CANCELED         → CANCELLED
 * PARTIAL_CANCELED → REFUND_PENDING
 * </pre>
 */
public final class TossStatusMapper {

  private static final Map<TossStatus, ReservationState> TO_INTERNAL = build();

  private TossStatusMapper() {}

  public static ReservationState toInternal(TossStatus status) {
    ReservationState mapped = TO_INTERNAL.get(status);
    if (mapped == null) {
      throw new IllegalArgumentException("Unknown TossStatus: " + status);
    }
    return mapped;
  }

  private static Map<TossStatus, ReservationState> build() {
    EnumMap<TossStatus, ReservationState> m = new EnumMap<>(TossStatus.class);
    m.put(TossStatus.READY, ReservationState.PAYMENT_PENDING);
    m.put(TossStatus.IN_PROGRESS, ReservationState.PAYMENT_PENDING);
    m.put(TossStatus.DONE, ReservationState.CAPTURED);
    m.put(TossStatus.ABORTED, ReservationState.FAILED);
    m.put(TossStatus.EXPIRED, ReservationState.FAILED);
    m.put(TossStatus.CANCELED, ReservationState.CANCELLED);
    m.put(TossStatus.PARTIAL_CANCELED, ReservationState.REFUND_PENDING);
    return m;
  }
}
