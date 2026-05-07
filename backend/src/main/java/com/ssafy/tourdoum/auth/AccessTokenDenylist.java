package com.ssafy.tourdoum.auth;

/**
 * Access token denylist — ADR-0011 BE-2 (#63).
 *
 * <p>logout / family revoke 시 access의 jti를 denylist에 추가. Filter는 매 요청 contains 체크. TTL은 access의 잔여
 * 만료 시간 — Redis가 자동 청소.
 */
public interface AccessTokenDenylist {

  /** denylist에 jti 추가 (TTL = access 잔여 만료 초 — 음수면 0). */
  void add(String jti, long ttlSeconds);

  /** 해당 jti가 denylist에 있는지. */
  boolean contains(String jti);
}
