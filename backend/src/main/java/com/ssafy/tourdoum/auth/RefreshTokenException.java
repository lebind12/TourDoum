package com.ssafy.tourdoum.auth;

/**
 * Refresh token 검증 실패 — 401 (#63, ADR-0011 BE-2).
 *
 * <p>유발 케이스: 만료/서명 오류, family 부재(폐기됨), replay 감지(currentJti/prevJti 매칭 실패).
 */
public class RefreshTokenException extends RuntimeException {
  public RefreshTokenException(String message) {
    super(message);
  }

  public RefreshTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
