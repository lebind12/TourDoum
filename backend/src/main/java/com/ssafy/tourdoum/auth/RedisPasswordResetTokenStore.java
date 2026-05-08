package com.ssafy.tourdoum.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 구현 — `auth:reset:{hash}` value=memberId, TTL=30분. */
@Component
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

  static final String KEY_PREFIX = "auth:reset:";

  private final StringRedisTemplate redis;

  public RedisPasswordResetTokenStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void save(String tokenHash, long memberId, Duration ttl) {
    redis.opsForValue().set(KEY_PREFIX + tokenHash, Long.toString(memberId), ttl);
  }

  @Override
  public long consumeOrMinusOne(String tokenHash) {
    String key = KEY_PREFIX + tokenHash;
    // GETDEL atomic — 6.2+. 학습 단계는 GET + DELETE 비원자 fallback.
    String v = redis.opsForValue().getAndDelete(key);
    if (v == null) {
      return -1L;
    }
    return Long.parseLong(v);
  }
}
