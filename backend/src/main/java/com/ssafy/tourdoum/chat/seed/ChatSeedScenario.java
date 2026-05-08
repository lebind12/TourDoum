package com.ssafy.tourdoum.chat.seed;

/** 채팅 seed 시나리오 — ADR-0012 v2 §Seed. */
public enum ChatSeedScenario {
  /** 1 PUBLIC 채널 + 1000 멤버 + 250만 메시지 (Zipf skew). */
  PUBLIC,
  /** 500 DM 채널 × 5000 메시지 = 250만 (멤버 pool에서 unique pair 추출). */
  DM,
  /** PUBLIC + DM 합본 (총 500만). */
  MIXED;

  public static ChatSeedScenario parse(String token) {
    if (token == null || token.isBlank()) {
      return MIXED;
    }
    return ChatSeedScenario.valueOf(token.trim().toUpperCase());
  }
}
