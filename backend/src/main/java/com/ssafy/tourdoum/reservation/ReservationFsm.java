package com.ssafy.tourdoum.reservation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reservation FSM 전이 규칙 + 보상 이벤트 매핑 — ADR-0013 §결정 (3).
 *
 * <pre>
 * QUEUED → ADMITTED → INVENTORY_RESERVED → PAYMENT_PENDING
 *                                          ├→ AUTHORIZED → CAPTURED → CONFIRMED
 *                                          │                          ├→ REVERSED
 *                                          │                          └→ REFUND_PENDING → REFUNDED
 *                                          ├→ REJECTED
 *                                          ├→ CANCELLED
 *                                          └→ FAILED
 * </pre>
 *
 * <p>추가 경로: AUTHORIZED → FAILED, INVENTORY_RESERVED → CANCELLED/FAILED 도 허용 (PG/사용자 액션).
 */
public final class ReservationFsm {

  /** target state → 허용된 from state 집합. {@link #transition} conditional UPDATE의 expectedPrev. */
  private static final Map<ReservationState, Set<ReservationState>> ALLOWED_PREV =
      buildAllowedPrev();

  /** target state → 발행할 보상/알림 outbox event 타입 (없으면 empty). */
  private static final Map<ReservationState, String> EMITS = buildEmits();

  private ReservationFsm() {}

  public static Set<ReservationState> allowedPrev(ReservationState target) {
    return ALLOWED_PREV.getOrDefault(target, EnumSet.noneOf(ReservationState.class));
  }

  /** target 진입 시 발행할 outbox event 타입(있으면). */
  public static Optional<String> emittedEventType(ReservationState target) {
    return Optional.ofNullable(EMITS.get(target));
  }

  /** legacy 디버그용 — 본 fsm이 인지하는 전이의 평탄 표현. */
  public static String describe(ReservationState from, ReservationState to) {
    return from + " → " + to;
  }

  private static Map<ReservationState, Set<ReservationState>> buildAllowedPrev() {
    EnumMap<ReservationState, Set<ReservationState>> m = new EnumMap<>(ReservationState.class);
    m.put(ReservationState.QUEUED, EnumSet.noneOf(ReservationState.class));
    m.put(ReservationState.ADMITTED, EnumSet.of(ReservationState.QUEUED));
    m.put(
        ReservationState.INVENTORY_RESERVED,
        EnumSet.of(ReservationState.ADMITTED, ReservationState.QUEUED));
    m.put(ReservationState.PAYMENT_PENDING, EnumSet.of(ReservationState.INVENTORY_RESERVED));
    m.put(ReservationState.AUTHORIZED, EnumSet.of(ReservationState.PAYMENT_PENDING));
    m.put(ReservationState.CAPTURED, EnumSet.of(ReservationState.AUTHORIZED));
    m.put(ReservationState.CONFIRMED, EnumSet.of(ReservationState.CAPTURED));
    m.put(ReservationState.REJECTED, EnumSet.of(ReservationState.PAYMENT_PENDING));
    m.put(
        ReservationState.CANCELLED,
        EnumSet.of(
            ReservationState.PAYMENT_PENDING,
            ReservationState.INVENTORY_RESERVED,
            ReservationState.ADMITTED));
    m.put(ReservationState.REVERSED, EnumSet.of(ReservationState.CONFIRMED));
    m.put(ReservationState.REFUND_PENDING, EnumSet.of(ReservationState.CONFIRMED));
    m.put(ReservationState.REFUNDED, EnumSet.of(ReservationState.REFUND_PENDING));
    m.put(
        ReservationState.FAILED,
        EnumSet.of(
            ReservationState.PAYMENT_PENDING,
            ReservationState.AUTHORIZED,
            ReservationState.INVENTORY_RESERVED));
    return m;
  }

  private static Map<ReservationState, String> buildEmits() {
    EnumMap<ReservationState, String> m = new EnumMap<>(ReservationState.class);
    // ADR-0013 §결정 (3) 분산 안전 코드 패턴 switch.
    m.put(ReservationState.INVENTORY_RESERVED, "PaymentRequested");
    m.put(ReservationState.CONFIRMED, "Notify");
    m.put(ReservationState.FAILED, "InventoryRelease");
    m.put(ReservationState.CANCELLED, "InventoryRelease");
    m.put(ReservationState.REFUND_PENDING, "RefundScheduled");
    return m;
  }
}
