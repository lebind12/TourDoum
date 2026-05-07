package com.ssafy.tourdoum.auth;

/**
 * `POST /api/auth/refresh` 요청 body — refresh token.
 *
 * <p><b>Deprecated (ADR-0011 BE-3):</b> SPA는 httpOnly cookie {@code refresh_token}으로 전송한다. 본 필드는
 * BE-2 contract와의 호환을 위해 cookie 부재 시 fallback로만 사용된다. FE-1 보강 cleanup 후 제거 예정.
 *
 * @param refreshToken cookie가 없을 때만 참조되는 fallback 값. cookie path 사용 시 비어 있어도 무방.
 */
public record RefreshRequest(String refreshToken) {}
