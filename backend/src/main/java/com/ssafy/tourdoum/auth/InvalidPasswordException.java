package com.ssafy.tourdoum.auth;

/** 비밀번호 정책 위반 — 400 Bad Request. ADR-0011 BE-4.2. */
public class InvalidPasswordException extends RuntimeException {
  public InvalidPasswordException(String message) {
    super(message);
  }
}
