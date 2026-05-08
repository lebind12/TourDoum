package com.ssafy.tourdoum.auth;

/** per-IP throttle 초과 — 429 Too Many Requests. ADR-0011 BE-4.4. */
public class TooManyLoginAttemptsException extends RuntimeException {
  public TooManyLoginAttemptsException(String message) {
    super(message);
  }
}
