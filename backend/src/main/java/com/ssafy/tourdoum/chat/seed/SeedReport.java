package com.ssafy.tourdoum.chat.seed;

import java.time.Duration;

/**
 * Seed 실행 결과 — handoff/QA-2 보고용.
 *
 * @param scenario 시나리오.
 * @param totalMessages 본 호출에서 INSERT한 메시지 수(멱등 skip이면 0).
 * @param totalChannels 본 호출에서 새로 만든 채널 수.
 * @param totalMembers 본 호출에서 새로 만든 회원 수.
 * @param skipped 멱등 sentinel hit으로 skip된 시나리오 수.
 * @param elapsed 전체 elapsed wall-clock.
 */
public record SeedReport(
    ChatSeedScenario scenario,
    long totalMessages,
    int totalChannels,
    int totalMembers,
    int skipped,
    Duration elapsed) {}
