package com.ssafy.tourdoum.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 cookie 빌더 — refresh token cookie set/clear (ADR-0011 BE-3).
 *
 * <p>속성은 {@link AuthCookieProperties}에서 주입. SameSite는 servlet cookie API가 직접 지원하지 않아 Spring {@link
 * ResponseCookie}로 빌드한 뒤 {@code Set-Cookie} 헤더에 박는다.
 */
@Component
public class AuthCookieService {

  private final AuthCookieProperties properties;

  public AuthCookieService(AuthCookieProperties properties) {
    this.properties = properties;
  }

  public String cookieName() {
    return properties.name();
  }

  /** refresh token 발급 cookie — TTL 초 단위. 0 이하이면 세션 cookie. */
  public ResponseCookie buildRefresh(String token, long ttlSeconds) {
    ResponseCookie.ResponseCookieBuilder b =
        ResponseCookie.from(properties.name(), token)
            .httpOnly(true)
            .secure(Boolean.TRUE.equals(properties.secure()))
            .sameSite(properties.sameSite())
            .path(properties.path());
    if (properties.domain() != null && !properties.domain().isBlank()) {
      b.domain(properties.domain());
    }
    if (ttlSeconds > 0) {
      b.maxAge(ttlSeconds);
    }
    return b.build();
  }

  /** refresh cookie clear (logout / replay 폐기 후) — Max-Age=0 + 같은 path/domain. */
  public ResponseCookie clearRefresh() {
    ResponseCookie.ResponseCookieBuilder b =
        ResponseCookie.from(properties.name(), "")
            .httpOnly(true)
            .secure(Boolean.TRUE.equals(properties.secure()))
            .sameSite(properties.sameSite())
            .path(properties.path())
            .maxAge(0);
    if (properties.domain() != null && !properties.domain().isBlank()) {
      b.domain(properties.domain());
    }
    return b.build();
  }
}
