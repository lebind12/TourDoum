package com.ssafy.tourdoum.outbox;

/** 외부 발행 큐 row의 claim 상태 — ADR-0013 §결정 (11). */
public enum OutboxClaimState {
  /** 신규 — publisher worker가 claim 대상. */
  PENDING,
  /** publisher가 처리 중. claimed_by + claimed_at TTL 30s. */
  CLAIMED,
  /** 정상 발행 완료. */
  DONE,
  /** 재시도 한도 초과 — outbox_dead_letter로 이관 후에도 본 row를 FAILED로 기록 (운영 가시성). */
  FAILED
}
