package com.ssafy.tourdoum.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Test 전용 in-memory {@link PasswordResetTokenStore} — atomic GETDEL emulation. */
public class InMemoryPasswordResetTokenStore implements PasswordResetTokenStore {

  private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

  @Override
  public void save(String tokenHash, long memberId, Duration ttl) {
    store.put(tokenHash, new Entry(memberId, Instant.now().plus(ttl)));
  }

  @Override
  public long consumeOrMinusOne(String tokenHash) {
    Entry e = store.remove(tokenHash);
    if (e == null || Instant.now().isAfter(e.expiresAt)) {
      return -1L;
    }
    return e.memberId;
  }

  public void clear() {
    store.clear();
  }

  private record Entry(long memberId, Instant expiresAt) {}
}
