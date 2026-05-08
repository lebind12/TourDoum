package com.ssafy.tourdoum.auth;

/**
 * 로그인 brute-force 방어 — ADR-0011 BE-4.4.
 *
 * <ul>
 *   <li>per-account 5회 실패 → 30분 자동 해제 잠금 ({@link LoginLockedException}).
 *   <li>per-IP 10회/10분 throttling ({@link TooManyLoginAttemptsException}).
 *   <li>관리자 계정은 자동 해제 X — 별도 endpoint(BE-4.1 후속 task).
 * </ul>
 *
 * <p>본 task는 일반 사용자만 다루며, 관리자 수동 해제는 후속.
 */
public interface LoginLockoutService {

  /**
   * 로그인 시도 전 호출 — 잠금/throttle 상태면 예외 throw. 통과 시 아무것도 안 함.
   *
   * @throws LoginLockedException per-account 잠금 상태
   * @throws TooManyLoginAttemptsException per-IP throttle 초과
   */
  void preCheck(String email, String ip);

  /** 로그인 실패 시 호출 — 카운터 증가. 임계 도달 시 잠금/throttle 전이. */
  void recordFailure(String email, String ip);

  /** 로그인 성공 시 호출 — per-account 카운터 clear. per-IP는 유지(다른 계정 보호). */
  void recordSuccess(String email);
}
