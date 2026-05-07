package com.ssafy.tourdoum.auth;

/**
 * `POST /api/auth/login` 응답 (#61, ADR-0011 BE-1).
 *
 * <p>SPA는 cookie 기반(BE-3)으로 전환되지만 BE-1에서는 Bearer header 응답으로 시작한다. FE-1에서 axios interceptor가 본 응답의
 * {@code accessToken}을 메모리에 보관하고 후속 요청에 {@code Authorization: Bearer} 로 첨부한다.
 *
 * @param accessToken RS256 서명된 JWT.
 * @param expiresInSeconds access TTL — 클라이언트 만료 추적용.
 * @param tokenType 항상 {@code "Bearer"}.
 * @param user 로그인된 회원 (기존 `MeResponse` 그대로).
 */
public record LoginResponse(
    String accessToken, long expiresInSeconds, String tokenType, MeResponse user) {

  public static LoginResponse of(JwtTokenProvider.IssuedToken issued, MeResponse user) {
    return new LoginResponse(issued.token(), issued.expiresInSeconds(), "Bearer", user);
  }
}
