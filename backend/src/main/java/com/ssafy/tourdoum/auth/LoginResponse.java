package com.ssafy.tourdoum.auth;

/**
 * `POST /api/auth/login` / `POST /api/auth/refresh` 응답 (#61 BE-1, #63 BE-2 확장).
 *
 * <p>SPA는 cookie 기반(BE-3)으로 전환되지만 BE-1/BE-2에서는 Bearer header 응답으로 시작. FE-1에서 axios interceptor가 본
 * 응답의 토큰들을 메모리에 보관하고 후속 요청에 첨부.
 *
 * @param accessToken RS256 서명된 access JWT.
 * @param expiresInSeconds access TTL.
 * @param tokenType 항상 {@code "Bearer"}.
 * @param refreshToken RS256 서명된 refresh JWT (#63 BE-2).
 * @param refreshExpiresInSeconds refresh TTL.
 * @param user 로그인 회원 정보.
 */
public record LoginResponse(
    String accessToken,
    long expiresInSeconds,
    String tokenType,
    String refreshToken,
    long refreshExpiresInSeconds,
    MeResponse user) {

  public static LoginResponse of(
      JwtTokenProvider.IssuedToken access, JwtTokenProvider.IssuedToken refresh, MeResponse user) {
    return new LoginResponse(
        access.token(),
        access.expiresInSeconds(),
        "Bearer",
        refresh.token(),
        refresh.expiresInSeconds(),
        user);
  }
}
