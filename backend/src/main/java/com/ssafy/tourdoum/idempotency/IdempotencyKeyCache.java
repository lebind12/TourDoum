package com.ssafy.tourdoum.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotency-Key Redis 캐시 — `SET NX EX` 15분(default).
 *
 * <p>{@link #cacheOrCompute}가 단일 진입점:
 *
 * <ul>
 *   <li>키 존재 → 캐시된 응답 그대로 반환 (재실행 X).
 *   <li>키 부재 → supplier 실행 → 결과 캐시 + 반환.
 * </ul>
 *
 * <p>BE-13 reservation의 DB UNIQUE INDEX 멱등성과 다른 layer — 본 캐시는 응답 재계산 회피용.
 *
 * <p>Redis 미가동 환경(test profile)을 위한 `InMemoryIdempotencyKeyCache` 별 구현 가능.
 */
@Component
public class IdempotencyKeyCache {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyCache.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  @Autowired
  public IdempotencyKeyCache(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${tourdoum.idempotency.ttl-seconds:900}") long ttlSeconds) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.ttl = Duration.ofSeconds(ttlSeconds);
  }

  /**
   * 캐시 hit 시 deserialize해서 반환, miss 시 supplier 실행 + cache + 반환.
   *
   * @param key clientKey (예: HTTP `Idempotency-Key` header 값)
   * @param namespace 키 네임스페이스 (예: "payment-start")
   * @param type 응답 클래스
   * @param supplier 캐시 miss 시 실행할 핸들러
   */
  public <T> T cacheOrCompute(
      String key, String namespace, Class<T> type, java.util.function.Supplier<T> supplier) {
    if (key == null || key.isBlank()) {
      return supplier.get();
    }
    String redisKey = "idempotency:" + namespace + ":" + key;
    String cached = redis.opsForValue().get(redisKey);
    if (cached != null) {
      try {
        return objectMapper.readValue(cached, type);
      } catch (JsonProcessingException ex) {
        LOG.warn("[idempotency] 캐시 deserialize 실패 — recompute (key={}): {}", key, ex.getMessage());
        // fallthrough to recompute
      }
    }
    T result = supplier.get();
    try {
      String serialized = objectMapper.writeValueAsString(result);
      // SET NX EX — 동시 요청 시 한 측만 저장. 다른 측은 다음 호출에서 hit.
      redis.opsForValue().setIfAbsent(redisKey, serialized, ttl);
    } catch (JsonProcessingException ex) {
      LOG.warn("[idempotency] 캐시 serialize 실패 — skip cache (key={}): {}", key, ex.getMessage());
    }
    return result;
  }

  public Optional<String> peek(String key, String namespace) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(redis.opsForValue().get("idempotency:" + namespace + ":" + key));
  }
}
