package com.ssafy.tourdoum.outbox;

/**
 * Outbox 이벤트 처리기 인터페이스 — ADR-0013 §결정 (11.4) idempotent consumer.
 *
 * <p>{@link OutboxPublisher}가 PENDING row를 claim 후 본 인터페이스의 {@link #handle}을 호출. handler 내부는
 * {@code idempotency_key = OutboxEvent#getId()}로 외부 호출(PG mock / Email / SSE)을 멱등 처리해야 한다.
 *
 * <p>예외를 던지면 publisher가 attempt_count++ + exponential backoff. 5회 초과 시 dead_letter 이관.
 */
public interface OutboxEventHandler {

  /** 이 handler가 처리할 event_type. */
  String eventType();

  /** 외부 발행. 실패 시 RuntimeException — publisher가 backoff/retry 처리. */
  void handle(OutboxEvent event);
}
