/**
 * R11 — 예약 FSM 타입 + 사용자 라벨 매핑 + 공통 envelope.
 *
 * Codex 5회차 정정 반영:
 *  - (3) 내부 enum을 사용자에게 그대로 노출 X. 사용자 라벨은 USER_STATE_LABEL.
 *  - (4) 공통 envelope = { code, message, retryAfter, idempotencyKey, serverTime, data }.
 *  - (4) Toss Payments 외부 status ↔ 내부 FSM 매핑 표는 TOSS_TO_INTERNAL.
 *  - (5) state별 색 토큰 매핑 = STATE_COLOR_TOKEN. semantic alias(--success/--warning) 우선 사용.
 *
 * 본 모듈은 fixture handler / Timeline / 사용자 메시지 계층의 단일 소스 오브 트루스.
 * 실 BE는 BE-13~15에서 박제. 본 라운드는 client mock에서만 참조.
 */

// ─────────────────────────────────────────────────────────────────────────────
// 1. 내부 FSM (server-side enum과 동치)
// ─────────────────────────────────────────────────────────────────────────────

export type ReservationState =
	| "QUEUED"
	| "ADMITTED"
	| "INVENTORY_RESERVED"
	| "PAYMENT_PENDING"
	| "AUTHORIZED"
	| "CAPTURED"
	| "CONFIRMED"
	| "REFUND_PENDING"
	| "REFUNDED"
	| "REJECTED"
	| "CANCELLED"
	| "FAILED"
	| "REVERSED";

// ─────────────────────────────────────────────────────────────────────────────
// 2. 사용자 노출 라벨 (Codex 정정 #3)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 내부 enum을 그대로 보여주지 않는다. UI가 직접 노출하는 텍스트는 본 표만 사용.
 * 내부 enum은 ReservationTimeline의 "상세" 접기 영역에서만 노출.
 */
export const USER_STATE_LABEL: Record<ReservationState, string> = {
	QUEUED: "대기 중",
	ADMITTED: "입장 허용",
	INVENTORY_RESERVED: "객실 보유",
	PAYMENT_PENDING: "결제 중",
	AUTHORIZED: "결제 중",
	CAPTURED: "결제 중",
	CONFIRMED: "예약 확정",
	REFUND_PENDING: "환불 진행 중",
	REFUNDED: "환불 완료",
	REJECTED: "예약 실패",
	CANCELLED: "예약 취소",
	FAILED: "예약 실패",
	REVERSED: "예약 취소",
};

// ─────────────────────────────────────────────────────────────────────────────
// 3. 색 토큰 매핑 (Codex 정정 #5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 본 표는 frontend/src/index.css에 정의된 CSS 변수를 가리킨다.
 * 컴포넌트는 hsl(var(...))로 합성해서 쓴다.
 */
export type StateColorToken =
	| "--success"
	| "--warning"
	| "--state-pending"
	| "--state-failed"
	| "--state-neutral";

export const STATE_COLOR_TOKEN: Record<ReservationState, StateColorToken> = {
	QUEUED: "--state-pending",
	ADMITTED: "--warning",
	INVENTORY_RESERVED: "--state-pending",
	PAYMENT_PENDING: "--state-pending",
	AUTHORIZED: "--success",
	CAPTURED: "--success",
	CONFIRMED: "--success",
	REFUND_PENDING: "--warning",
	REFUNDED: "--state-neutral",
	REJECTED: "--state-failed",
	CANCELLED: "--state-failed",
	FAILED: "--state-failed",
	REVERSED: "--state-failed",
};

// ─────────────────────────────────────────────────────────────────────────────
// 4. 공통 envelope (Codex 정정 #4)
// ─────────────────────────────────────────────────────────────────────────────

export type ApiCode =
	| "OK"
	| "QUEUE_FULL"
	| "TOKEN_EXPIRED"
	| "ADMISSION_EXPIRED"
	| "INVENTORY_RELEASED"
	| "PAYMENT_REJECTED"
	| "RATE_LIMITED"
	| "UNAUTHORIZED"
	| "FORBIDDEN"
	| "INTERNAL_ERROR";

export interface ApiEnvelope<T> {
	code: ApiCode;
	/** 사용자 노출용 메시지 (라벨 표와 함께 사용). */
	message: string;
	/** 다음 재시도까지 대기 권장 초. RATE_LIMITED / 503 케이스. */
	retryAfter?: number;
	/** POST 멱등 키. 클라이언트가 발행하고 BE가 echo. */
	idempotencyKey?: string;
	/** 서버 wall clock (ISO). 클라이언트 시계 보정용. */
	serverTime: string;
	data: T | null;
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Queue API contract
// ─────────────────────────────────────────────────────────────────────────────

export type QueueStatus = "WAITING" | "ADMITTED" | "EXPIRED";

export interface QueueStatusData {
	status: QueueStatus;
	pollAfterMs: number;
	/** queue token (heartbeat로 연장 가능 — Codex 정정 #2). */
	queueTokenExpiresAt: string;
	/** admission token (5분 고정). admitted 상태에서만 non-null. */
	admissionExpiresAt: string | null;
	reservationId: string | null;
	position: number;
	/** 예상 입장 시각 ISO. */
	eta: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Payment API contract — Toss Payments 매핑 (Codex 정정 #4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Toss Payments 공식 status (https://docs.tosspayments.com/reference).
 * READY → IN_PROGRESS → DONE / ABORTED / EXPIRED 단순화.
 */
export type TossStatus =
	| "READY"
	| "IN_PROGRESS"
	| "DONE"
	| "ABORTED"
	| "EXPIRED"
	| "CANCELED"
	| "PARTIAL_CANCELED";

export const TOSS_TO_INTERNAL: Record<TossStatus, ReservationState> = {
	READY: "PAYMENT_PENDING",
	IN_PROGRESS: "PAYMENT_PENDING",
	DONE: "CAPTURED",
	ABORTED: "FAILED",
	EXPIRED: "FAILED",
	CANCELED: "CANCELLED",
	PARTIAL_CANCELED: "REFUND_PENDING",
};

export interface PaymentStartData {
	reservationId: string;
	state: Extract<ReservationState, "PAYMENT_PENDING">;
	tossStatus: TossStatus;
	redirectTo: string;
}
