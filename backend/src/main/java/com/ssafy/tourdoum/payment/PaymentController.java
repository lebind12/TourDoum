package com.ssafy.tourdoum.payment;

import com.ssafy.tourdoum.idempotency.IdempotencyKeyCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG 결제 (mock) — ADR-0013 Phase 1 BE-14.
 *
 * <p>응답 envelope = ui R11 `ApiEnvelope` 와 1:1 동치. Idempotency-Key 헤더로 응답 캐시 (Redis 15분).
 */
@Tag(name = "Payment", description = "PG 결제 mock + Toss status 매핑")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  private final PaymentMockService paymentMockService;
  private final IdempotencyKeyCache idempotencyCache;

  public PaymentController(
      PaymentMockService paymentMockService, IdempotencyKeyCache idempotencyCache) {
    this.paymentMockService = paymentMockService;
    this.idempotencyCache = idempotencyCache;
  }

  @Operation(
      summary = "결제 시작 (PG mock)",
      description =
          "ReservationState INVENTORY_RESERVED → PAYMENT_PENDING → AUTHORIZED → CAPTURED → CONFIRMED 동기 시뮬."
              + " Idempotency-Key 헤더 동봉 시 응답 15분 캐시.")
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/start")
  public ResponseEntity<ApiEnvelope<PaymentStartData>> start(
      @Valid @RequestBody PaymentStartRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    @SuppressWarnings({"unchecked", "rawtypes"})
    ApiEnvelope<PaymentStartData> envelope =
        (ApiEnvelope)
            idempotencyCache.cacheOrCompute(
                idempotencyKey,
                "payment-start",
                ApiEnvelope.class,
                () -> {
                  PaymentStartData data = paymentMockService.start(request.reservationId());
                  return ApiEnvelope.ok(data, idempotencyKey);
                });
    return ResponseEntity.ok(envelope);
  }
}
