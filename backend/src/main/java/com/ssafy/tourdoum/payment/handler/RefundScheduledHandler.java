package com.ssafy.tourdoum.payment.handler;

import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxEventHandler;
import com.ssafy.tourdoum.reservation.ReservationService;
import com.ssafy.tourdoum.reservation.ReservationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * `RefundScheduled` outbox 이벤트 handler — ADR-0013 §결정 (4) REFUND 잡스케줄러.
 *
 * <p>BE-15 publisher가 `available_at = REFUND_PENDING + 3min`이 도래한 outbox row를 claim하고
 * 본 handler에 전달. 본 handler는 REFUND_PENDING → REFUNDED 전이를 시도한다 (멱등 conditional UPDATE).
 *
 * <p>전이 후 ReservationService가 자동으로 보상 outbox를 발행하지는 않으므로, 사용자 알림용
 * `Notify` 이벤트는 본 handler에서 재발행할 책임이 있다 — 다만 현 단계에선 transitionTo 호출만으로
 * 도메인 흐름이 끝난다고 가정. 상위 task(ADMIN-1)에서 사용자 알림 emit 정책 박제 권고.
 *
 * <p>idempotency: outbox.id 자체가 멱등 키. 동일 row가 재시도되어도 conditional UPDATE가
 * REFUND_PENDING → REFUNDED 한 번만 성공, 이후 호출은 0 row 무영향.
 */
@Component
public class RefundScheduledHandler implements OutboxEventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RefundScheduledHandler.class);

  private final ReservationService reservationService;

  public RefundScheduledHandler(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @Override
  public String eventType() {
    return "RefundScheduled";
  }

  @Override
  public void handle(OutboxEvent event) {
    Long reservationId = event.getAggregateId();
    boolean transitioned =
        reservationService.transitionTo(reservationId, ReservationState.REFUNDED);
    LOG.info(
        "[refund-handler] outbox.id={} reservationId={} REFUND_PENDING → REFUNDED transitioned={}",
        event.getId(),
        reservationId,
        transitioned);
  }
}
