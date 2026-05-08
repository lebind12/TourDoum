package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LoginLockoutService 단위 테스트 (InMemory) — ADR-0011 BE-4.4. */
class LoginLockoutServiceTest {

  @Test
  @DisplayName("계정 5회 실패 → preCheck에서 LoginLockedException(423)")
  void account_threshold_locks() {
    InMemoryLoginLockoutService svc =
        new InMemoryLoginLockoutService(5, Duration.ofMinutes(30), 100, Duration.ofMinutes(10));
    String email = "a@example.com";
    String ip = "127.0.0.1";
    for (int i = 0; i < 5; i++) {
      svc.recordFailure(email, ip);
    }
    assertThatThrownBy(() -> svc.preCheck(email, ip)).isInstanceOf(LoginLockedException.class);
  }

  @Test
  @DisplayName("성공 시 카운터 초기화 → 다음 실패는 잠금 안 됨")
  void success_resets() {
    InMemoryLoginLockoutService svc =
        new InMemoryLoginLockoutService(3, Duration.ofMinutes(30), 100, Duration.ofMinutes(10));
    String email = "b@example.com";
    svc.recordFailure(email, "1.1.1.1");
    svc.recordFailure(email, "1.1.1.1");
    svc.recordSuccess(email);
    svc.recordFailure(email, "1.1.1.1"); // 1회만
    assertThatCode(() -> svc.preCheck(email, "1.1.1.1")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("IP throttle 초과 → TooManyLoginAttemptsException(429)")
  void ip_throttle_triggers() {
    InMemoryLoginLockoutService svc =
        new InMemoryLoginLockoutService(100, Duration.ofMinutes(30), 5, Duration.ofMinutes(10));
    String ip = "9.9.9.9";
    for (int i = 0; i < 5; i++) {
      svc.recordFailure("u" + i + "@e.com", ip);
    }
    assertThatThrownBy(() -> svc.preCheck("new@example.com", ip))
        .isInstanceOf(TooManyLoginAttemptsException.class);
  }

  @Test
  @DisplayName("expireAccountLock으로 잠금 즉시 해제")
  void expire_unlocks() {
    InMemoryLoginLockoutService svc =
        new InMemoryLoginLockoutService(2, Duration.ofMinutes(30), 100, Duration.ofMinutes(10));
    String email = "c@example.com";
    svc.recordFailure(email, "8.8.8.8");
    svc.recordFailure(email, "8.8.8.8");
    assertThatThrownBy(() -> svc.preCheck(email, "8.8.8.8"))
        .isInstanceOf(LoginLockedException.class);
    svc.expireAccountLock(email);
    assertThatCode(() -> svc.preCheck(email, "8.8.8.8")).doesNotThrowAnyException();
  }
}
