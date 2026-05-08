package com.ssafy.tourdoum.auth;

/** 계정 잠금(per-account brute-force) — 423 Locked. ADR-0011 BE-4.4. */
public class LoginLockedException extends RuntimeException {
  public LoginLockedException(String message) {
    super(message);
  }
}
