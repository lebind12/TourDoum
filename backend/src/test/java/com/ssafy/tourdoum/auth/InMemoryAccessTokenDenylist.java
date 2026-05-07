package com.ssafy.tourdoum.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** AccessTokenDenylist in-memory test impl. TTL은 무시 — 테스트가 짧아 만료 시뮬 불필요. */
public class InMemoryAccessTokenDenylist implements AccessTokenDenylist {

  private final ConcurrentMap<String, Boolean> store = new ConcurrentHashMap<>();

  @Override
  public void add(String jti, long ttlSeconds) {
    store.put(jti, Boolean.TRUE);
  }

  @Override
  public boolean contains(String jti) {
    return store.containsKey(jti);
  }

  public void clear() {
    store.clear();
  }
}
