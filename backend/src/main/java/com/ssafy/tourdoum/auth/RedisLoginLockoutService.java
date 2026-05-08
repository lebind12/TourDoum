package com.ssafy.tourdoum.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link LoginLockoutService} Redis 구현 — ADR-0011 BE-4.4.
 *
 * <p>키 구조:
 *
 * <ul>
 *   <li>{@code auth:login:fail:account:{email}} — 실패 카운터, TTL은 잠금 윈도우 동안 유지.
 *   <li>{@code auth:login:locked:account:{email}} — 잠금 표식 + 자동 해제 TTL.
 *   <li>{@code auth:login:fail:ip:{ip}} — IP 카운터, 10분 sliding window TTL.
 * </ul>
 *
 * <p>임계값/TTL은 `tourdoum.auth.lockout.*` 프로퍼티로 override 가능 — IT가 짧게 fast-forward.
 */
@Component
public class RedisLoginLockoutService implements LoginLockoutService {

  static final String KEY_FAIL_ACCOUNT = "auth:login:fail:account:";
  static final String KEY_LOCKED_ACCOUNT = "auth:login:locked:account:";
  static final String KEY_FAIL_IP = "auth:login:fail:ip:";

  private final StringRedisTemplate redis;
  private final int accountThreshold;
  private final Duration accountLockTtl;
  private final int ipThreshold;
  private final Duration ipWindow;

  public RedisLoginLockoutService(
      StringRedisTemplate redis,
      @Value("${tourdoum.auth.lockout.account-threshold:5}") int accountThreshold,
      @Value("${tourdoum.auth.lockout.account-lock-seconds:1800}") long accountLockSeconds,
      @Value("${tourdoum.auth.lockout.ip-threshold:10}") int ipThreshold,
      @Value("${tourdoum.auth.lockout.ip-window-seconds:600}") long ipWindowSeconds) {
    this.redis = redis;
    this.accountThreshold = accountThreshold;
    this.accountLockTtl = Duration.ofSeconds(accountLockSeconds);
    this.ipThreshold = ipThreshold;
    this.ipWindow = Duration.ofSeconds(ipWindowSeconds);
  }

  @Override
  public void preCheck(String email, String ip) {
    if (email != null && Boolean.TRUE.equals(redis.hasKey(KEY_LOCKED_ACCOUNT + email))) {
      throw new LoginLockedException("계정이 잠겼습니다. 잠시 후 다시 시도해 주세요.");
    }
    if (ip != null) {
      String v = redis.opsForValue().get(KEY_FAIL_IP + ip);
      if (v != null && Integer.parseInt(v) >= ipThreshold) {
        throw new TooManyLoginAttemptsException("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  }

  @Override
  public void recordFailure(String email, String ip) {
    if (email != null && !email.isBlank()) {
      String key = KEY_FAIL_ACCOUNT + email;
      Long n = redis.opsForValue().increment(key);
      if (n != null && n == 1) {
        redis.expire(key, accountLockTtl);
      }
      if (n != null && n >= accountThreshold) {
        redis.opsForValue().set(KEY_LOCKED_ACCOUNT + email, "1", accountLockTtl);
      }
    }
    if (ip != null && !ip.isBlank()) {
      String key = KEY_FAIL_IP + ip;
      Long n = redis.opsForValue().increment(key);
      if (n != null && n == 1) {
        redis.expire(key, ipWindow);
      }
    }
  }

  @Override
  public void recordSuccess(String email) {
    if (email != null && !email.isBlank()) {
      redis.delete(KEY_FAIL_ACCOUNT + email);
      redis.delete(KEY_LOCKED_ACCOUNT + email);
    }
  }
}
