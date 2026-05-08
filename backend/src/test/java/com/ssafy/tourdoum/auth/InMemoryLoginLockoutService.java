package com.ssafy.tourdoum.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Test 전용 in-memory {@link LoginLockoutService} — ADR-0011 BE-4.4.
 *
 * <p>thresholds/TTL을 ctor로 주입해 IT가 fast-forward 가능. 기본은 production과 동일.
 */
public class InMemoryLoginLockoutService implements LoginLockoutService {

  private final int accountThreshold;
  private final Duration accountLockTtl;
  private final int ipThreshold;
  private final Duration ipWindow;

  private final ConcurrentMap<String, Counter> accountFails = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Instant> accountLockedUntil = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> ipFails = new ConcurrentHashMap<>();

  public InMemoryLoginLockoutService() {
    this(5, Duration.ofMinutes(30), 10, Duration.ofMinutes(10));
  }

  public InMemoryLoginLockoutService(
      int accountThreshold, Duration accountLockTtl, int ipThreshold, Duration ipWindow) {
    this.accountThreshold = accountThreshold;
    this.accountLockTtl = accountLockTtl;
    this.ipThreshold = ipThreshold;
    this.ipWindow = ipWindow;
  }

  @Override
  public void preCheck(String email, String ip) {
    if (email != null) {
      Instant until = accountLockedUntil.get(email);
      if (until != null) {
        if (Instant.now().isBefore(until)) {
          throw new LoginLockedException("계정이 잠겼습니다. 잠시 후 다시 시도해 주세요.");
        }
        accountLockedUntil.remove(email);
        accountFails.remove(email);
      }
    }
    if (ip != null) {
      Counter c = ipFails.get(ip);
      if (c != null && Instant.now().isBefore(c.expiresAt) && c.count >= ipThreshold) {
        throw new TooManyLoginAttemptsException("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  }

  @Override
  public void recordFailure(String email, String ip) {
    if (email != null && !email.isBlank()) {
      Counter c = accountFails.computeIfAbsent(
          email, k -> new Counter(0, Instant.now().plus(accountLockTtl)));
      c.count++;
      if (c.count >= accountThreshold) {
        accountLockedUntil.put(email, Instant.now().plus(accountLockTtl));
      }
    }
    if (ip != null && !ip.isBlank()) {
      Counter c = ipFails.computeIfAbsent(ip, k -> new Counter(0, Instant.now().plus(ipWindow)));
      if (Instant.now().isAfter(c.expiresAt)) {
        c.count = 0;
        c.expiresAt = Instant.now().plus(ipWindow);
      }
      c.count++;
    }
  }

  @Override
  public void recordSuccess(String email) {
    if (email != null && !email.isBlank()) {
      accountFails.remove(email);
      accountLockedUntil.remove(email);
    }
  }

  /** 테스트 편의 — 모든 카운터 초기화. */
  public void clear() {
    accountFails.clear();
    accountLockedUntil.clear();
    ipFails.clear();
  }

  /** 테스트 편의 — account lock TTL을 즉시 만료. */
  public void expireAccountLock(String email) {
    accountLockedUntil.remove(email);
    accountFails.remove(email);
  }

  private static final class Counter {
    int count;
    Instant expiresAt;

    Counter(int c, Instant e) {
      this.count = c;
      this.expiresAt = e;
    }
  }
}
