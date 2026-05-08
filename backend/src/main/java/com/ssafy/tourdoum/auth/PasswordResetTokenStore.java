package com.ssafy.tourdoum.auth;

import java.time.Duration;

/**
 * Reset 토큰 저장소 — ADR-0011 BE-4.5.
 *
 * <p>plain 토큰은 클라이언트(이메일 링크)에 한 번 노출. 서버는 SHA-256 해시만 저장. 사용 즉시 atomic delete + 30분 TTL.
 *
 * <p>학습 단계 본인확인은 콘솔 출력 mock — `PasswordResetService.initiate`가 plain 토큰을 로그에 박는다.
 */
public interface PasswordResetTokenStore {

  /** 해시 → 사용자 ID 박제. TTL = ttl. */
  void save(String tokenHash, long memberId, Duration ttl);

  /**
   * 해시로 사용자 조회 + 즉시 invalidate. atomic 1회용.
   *
   * @return 박제된 memberId, 토큰이 없거나 이미 사용됐으면 -1.
   */
  long consumeOrMinusOne(String tokenHash);
}
