package com.ssafy.tourdoum.auth;

import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link UserRevocationStore} Redis 구현 — `auth:rev-epoch:{memberId}` epoch seconds value. TTL 미설정
 * (refresh max 14d 만료까지 의미가 있고 그 이후 자연 lapse).
 */
@Component
public class RedisUserRevocationStore implements UserRevocationStore {

  static final String KEY_PREFIX = "auth:rev-epoch:";

  private final StringRedisTemplate redis;

  public RedisUserRevocationStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public long currentEpoch(long memberId) {
    String v = redis.opsForValue().get(KEY_PREFIX + memberId);
    return v == null ? 0L : Long.parseLong(v);
  }

  @Override
  public void bump(long memberId) {
    redis.opsForValue().set(KEY_PREFIX + memberId, Long.toString(Instant.now().getEpochSecond()));
  }
}
