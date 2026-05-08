package com.ssafy.tourdoum.payment.handler;

import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * `PaymentRequested` outbox 이벤트 handler — ADR-0013 §결정 (3) outbox dispatch.
 *
 * <p>본 단계에선 실 PG 호출을 시뮬하지 않고 로그만 남긴다. 실제 결제 시작은 사용자가
 * `POST /api/payments/start`를 호출 → `PaymentMockService`가 동기 체인 처리. 따라서
 * `PaymentRequested` outbox는 운영 가시성/감사 로그 용도로만 활용된다.
 *
 * <p>(별 task) Phase 2에서 PG redirect URL push 알림 등으로 확장 검토.
 */
@Component
public class PaymentRequestedHandler implements OutboxEventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentRequestedHandler.class);

  @Override
  public String eventType() {
    return "PaymentRequested";
  }

  @Override
  public void handle(OutboxEvent event) {
    LOG.info(
        "[payment-requested] outbox.id={} reservationId={} (감사 로그 — 결제 시작 트리거 대기 중)",
        event.getId(),
        event.getAggregateId());
  }
}
