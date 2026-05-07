package com.ssafy.tourdoum.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** {@link AccessTokenDenylist} Redis 구현 — `auth:denylist:{jti}` 빈 값 + TTL 자동 만료. */
@Component
public class RedisAccessTokenDenylist implements AccessTokenDenylist {

  static final String KEY_PREFIX = "auth:denylist:";

  private final StringRedisTemplate redis;

  public RedisAccessTokenDenylist(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void add(String jti, long ttlSeconds) {
    if (ttlSeconds <= 0) {
      // 이미 만료된 토큰은 굳이 박을 필요 없음.
      return;
    }
    redis.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
  }

  @Override
  public boolean contains(String jti) {
    Boolean exists = redis.hasKey(KEY_PREFIX + jti);
    return Boolean.TRUE.equals(exists);
  }
}
