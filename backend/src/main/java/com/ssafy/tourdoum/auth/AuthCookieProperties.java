package com.ssafy.tourdoum.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 cookie 정책 — `tourdoum.auth.cookie.*` (ADR-0011 BE-3).
 *
 * <p>refresh token을 httpOnly + Secure + SameSite=Strict cookie로 박는다. localhost http dev 환경에선 {@code
 * secure=false}로 토글해 cookie가 적용되도록 한다.
 *
 * @param name cookie 이름.
 * @param path cookie path — `/api/auth`로 좁혀 다른 경로엔 동봉되지 않도록 한다.
 * @param domain cookie 도메인. null이면 미설정(브라우저가 origin host로 처리).
 * @param secure Secure 플래그. 운영 https=true, localhost http dev=false.
 * @param sameSite SameSite 정책 — `Strict`(기본) / `Lax` / `None`.
 */
@ConfigurationProperties(prefix = "tourdoum.auth.cookie")
public record AuthCookieProperties(
    String name, String path, String domain, Boolean secure, String sameSite) {

  public AuthCookieProperties {
    if (name == null || name.isBlank()) {
      name = "refresh_token";
    }
    if (path == null || path.isBlank()) {
      path = "/api/auth";
    }
    if (secure == null) {
      secure = Boolean.TRUE;
    }
    if (sameSite == null || sameSite.isBlank()) {
      sameSite = "Strict";
    }
  }
}
