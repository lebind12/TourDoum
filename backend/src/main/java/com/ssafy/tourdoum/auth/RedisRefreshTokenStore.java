package com.ssafy.tourdoum.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link RefreshTokenStore} Redis 구현 — `auth:refresh:{family_id}` JSON value.
 *
 * <p>StringRedisTemplate 사용 — Jackson으로 record ↔ JSON 직렬화. TTL은 family.expiresAt 기반.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

  static final String KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public RedisRefreshTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(RefreshTokenFamily family) {
    String key = KEY_PREFIX + family.familyId();
    String json;
    try {
      json = objectMapper.writeValueAsString(family);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("RefreshTokenFamily JSON 직렬화 실패", e);
    }
    Duration ttl = Duration.between(Instant.now(), family.expiresAt());
    if (ttl.isNegative() || ttl.isZero()) {
      // 만료 시각이 이미 지났으면 저장 안 함. 호출자가 expiresAt을 미래로 세팅하는 책임.
      redis.delete(key);
      return;
    }
    redis.opsForValue().set(key, json, ttl);
  }

  @Override
  public Optional<RefreshTokenFamily> find(String familyId) {
    String json = redis.opsForValue().get(KEY_PREFIX + familyId);
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, RefreshTokenFamily.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("RefreshTokenFamily JSON 역직렬화 실패", e);
    }
  }

  @Override
  public void delete(String familyId) {
    redis.delete(KEY_PREFIX + familyId);
  }
}
