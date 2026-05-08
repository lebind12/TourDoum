package com.ssafy.tourdoum.payment;

/**
 * Toss Payments 공식 status 매핑 — https://docs.tosspayments.com/reference.
 *
 * <p>ui R11 `frontend/src/types/reservation.ts` `TossStatus` 와 1:1 동치.
 */
public enum TossStatus {
  READY,
  IN_PROGRESS,
  DONE,
  ABORTED,
  EXPIRED,
  CANCELED,
  PARTIAL_CANCELED
}
