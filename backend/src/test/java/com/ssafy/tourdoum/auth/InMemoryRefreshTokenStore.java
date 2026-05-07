package com.ssafy.tourdoum.auth;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** RefreshTokenStore in-memory test impl — Redis 미가동 슬라이스/스모크용. */
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

  private final ConcurrentMap<String, RefreshTokenFamily> store = new ConcurrentHashMap<>();

  @Override
  public void save(RefreshTokenFamily family) {
    store.put(family.familyId(), family);
  }

  @Override
  public Optional<RefreshTokenFamily> find(String familyId) {
    return Optional.ofNullable(store.get(familyId));
  }

  @Override
  public void delete(String familyId) {
    store.remove(familyId);
  }

  /** 테스트 헬퍼 — 전체 비움. */
  public void clear() {
    store.clear();
  }
}
