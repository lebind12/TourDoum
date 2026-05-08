package com.ssafy.tourdoum.auth;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Test 전용 — Redis 미가동 환경용 in-memory revocation store (ADR-0011 BE-4.3). */
public class InMemoryUserRevocationStore implements UserRevocationStore {

  private final ConcurrentMap<Long, Long> epochs = new ConcurrentHashMap<>();

  @Override
  public long currentEpoch(long memberId) {
    return epochs.getOrDefault(memberId, 0L);
  }

  @Override
  public void bump(long memberId) {
    epochs.put(memberId, Instant.now().getEpochSecond());
  }

  public void clear() {
    epochs.clear();
  }
}
