package com.ssafy.tourdoum.auth;

import java.util.Optional;

/**
 * Refresh token family 저장소 — ADR-0011 BE-2 (#63).
 *
 * <p>구현체는 Redis(prod) / InMemory(test). family 폐기 시 즉시 delete.
 */
public interface RefreshTokenStore {

  /** family 저장(또는 갱신). TTL은 expiresAt - now. */
  void save(RefreshTokenFamily family);

  /** family 조회. */
  Optional<RefreshTokenFamily> find(String familyId);

  /** family 폐기 (replay 감지 또는 logout). */
  void delete(String familyId);
}
