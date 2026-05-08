package com.ssafy.tourdoum.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BE-15 본 task 단계의 in-process 로그-only handler 묶음. 각 reservation FSM 보상/알림 이벤트를 일단
 * 흡수해 dead_letter 즉시 이관(orphan)되지 않도록 박제. 실제 외부 호출(PG mock / Email / SSE)은
 * BE-14 + ADMIN-1 별 task에서 본 handler들을 대체한다.
 *
 * <p>handler들이 실제 외부 시스템 호출 없이 INFO 로그만 남기므로 항상 성공 → markDone. 멱등성은 자동 보장.
 */
public final class InProcessLoggingHandlers {

  private InProcessLoggingHandlers() {}

  abstract static class LoggingHandler implements OutboxEventHandler {
    static final Logger LOG = LoggerFactory.getLogger("outbox.handler");

    @Override
    public void handle(OutboxEvent event) {
      LOG.info(
          "[outbox-handler] type={} aggregateId={} payload={} (in-process log only — BE-14/ADMIN-1에서 외부 호출로 교체 예정)",
          event.getEventType(),
          event.getAggregateId(),
          event.getPayload());
    }
  }

  // BE-14: PaymentRequestedHandler + RefundScheduledHandler는 실제 handler로 교체됨
  // (com.ssafy.tourdoum.payment.handler.* 참조). Notify / InventoryRelease는 ADMIN-1 /
  // 알림 도메인 task에서 외부 호출로 교체 예정.

  @Component
  public static class NotifyHandler extends LoggingHandler {
    @Override
    public String eventType() {
      return "Notify";
    }
  }

  @Component
  public static class InventoryReleaseHandler extends LoggingHandler {
    @Override
    public String eventType() {
      return "InventoryRelease";
    }
  }
}
