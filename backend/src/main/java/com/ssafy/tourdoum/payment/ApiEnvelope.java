package com.ssafy.tourdoum.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 공통 응답 envelope — ui R11 `ApiEnvelope` 와 1:1 동치 박제.
 *
 * <pre>
 * { code, message, retryAfter?, idempotencyKey?, serverTime, data }
 * </pre>
 *
 * @param <T> data 타입
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(
    String code,
    String message,
    Integer retryAfter,
    String idempotencyKey,
    String serverTime,
    T data) {

  public static <T> ApiEnvelope<T> ok(T data, String idempotencyKey) {
    return new ApiEnvelope<>("OK", "OK", null, idempotencyKey, Instant.now().toString(), data);
  }

  public static <T> ApiEnvelope<T> error(String code, String message, String idempotencyKey) {
    return new ApiEnvelope<>(code, message, null, idempotencyKey, Instant.now().toString(), null);
  }
}
