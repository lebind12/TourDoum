package com.ssafy.tourdoum.auth;

/** Reset 토큰 무효 — 400 Bad Request. ADR-0011 BE-4.5. */
public class InvalidResetTokenException extends RuntimeException {
  public InvalidResetTokenException(String message) {
    super(message);
  }
}
