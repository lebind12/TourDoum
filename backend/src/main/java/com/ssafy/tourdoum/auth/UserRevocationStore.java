package com.ssafy.tourdoum.auth;

/**
 * 사용자별 revocation epoch 저장소 — ADR-0011 BE-4.3.
 *
 * <p>password 변경/reset 성공 시 {@link #bump} 호출 → 해당 epoch 이전에 발급된 access/refresh 토큰을 모두 revoke한다.
 * 새 코드 최소화 원칙(ADR §BE-4.3): per-jti 추적 대신 단일 user epoch 비교.
 *
 * <p>JwtAuthenticationFilter / AuthService.rotate가 token의 {@code iat}와 epoch를 비교해 invalidate.
 */
public interface UserRevocationStore {

  /** 현재 epoch(seconds). 미설정 사용자는 0. */
  long currentEpoch(long memberId);

  /** epoch를 now()로 갱신 — 모든 기존 토큰 무효. */
  void bump(long memberId);
}
