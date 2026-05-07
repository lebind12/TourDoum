package com.ssafy.tourdoum.auth;

import java.time.Instant;

/**
 * Refresh token family snapshot — Redis 저장 단위 (#63, ADR-0011 BE-2).
 *
 * <p>한 로그인 세션 단위. rotation 시 currentJti가 갱신되며 prevJti에 직전 값이 남는다(네트워크 race grace 1회 허용). replay
 * 시(currentJti도 prevJti도 아닌 jti 도착) family 전체 폐기 + 현재 access denylist.
 *
 * @param familyId 첫 로그인에서 생성된 UUID. rotation 시 동일 유지.
 * @param userId 소유자 회원 PK.
 * @param currentJti 현재 활성 refresh의 jti.
 * @param prevJti 직전 refresh의 jti (network race grace 1회 허용 분기). 없으면 null.
 * @param currentAccessJti 현재 발급된 access의 jti — logout/revoke 시 denylist 박제용.
 * @param issuedAt 첫 발급 시각.
 * @param expiresAt 만료 시각 (refresh ttl 적용).
 */
public record RefreshTokenFamily(
    String familyId,
    Long userId,
    String currentJti,
    String prevJti,
    String currentAccessJti,
    Instant issuedAt,
    Instant expiresAt) {

  public RefreshTokenFamily withRotation(String newRefreshJti, String newAccessJti) {
    return new RefreshTokenFamily(
        familyId, userId, newRefreshJti, currentJti, newAccessJti, issuedAt, expiresAt);
  }
}
