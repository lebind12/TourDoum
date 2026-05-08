/**
 * R11 — 예약 fixture handler.
 *
 * BE-13~15 머지 전, lab 라우트(/dev/r11-reservation-lab)에서 시각 검증 + 단위 테스트
 * 용도로 사용. 실 fetch 가로채기 (msw)는 본 라운드 범위 외 — pure 함수만 박제하고
 * 컴포넌트가 직접 import해서 호출. Codex 정정 #8 (실 PG/webhook X) 준수.
 */
import type {
	ApiEnvelope,
	PaymentStartData,
	QueueStatusData,
	ReservationState,
	TossStatus,
} from "@/types/reservation";
import { TOSS_TO_INTERNAL, USER_STATE_LABEL } from "@/types/reservation";

const SERVER_NOW = () => new Date().toISOString();

// ─────────────────────────────────────────────────────────────────────────────
// envelope helper
// ─────────────────────────────────────────────────────────────────────────────

export function ok<T>(
	data: T,
	message = USER_STATE_LABEL.QUEUED,
): ApiEnvelope<T> {
	return {
		code: "OK",
		message,
		serverTime: SERVER_NOW(),
		data,
	};
}

export function err<T>(
	code: ApiEnvelope<T>["code"],
	message: string,
	retryAfter?: number,
): ApiEnvelope<T> {
	return {
		code,
		message,
		retryAfter,
		serverTime: SERVER_NOW(),
		data: null,
	};
}

// ─────────────────────────────────────────────────────────────────────────────
// queue status fixture
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 입장 대기 lobby의 polling 응답을 모사.
 * @param tickSeconds 가상 경과 시간 (테스트에서 시계 주입).
 *                    실제로는 5~10s polling으로 호출되며, BE가 position을 감소시킨다.
 */
export function fixtureQueueStatus(opts: {
	tickSeconds: number;
	initialPosition?: number;
	initialTokenTtlSeconds?: number;
}): ApiEnvelope<QueueStatusData> {
	const initialPosition = opts.initialPosition ?? 1247;
	const initialTokenTtl = opts.initialTokenTtlSeconds ?? 1800; // 30분 — heartbeat로 연장.

	// position은 매 tick마다 감소 (간단한 mock). 0 도달 시 ADMITTED.
	const position = Math.max(0, initialPosition - opts.tickSeconds * 5);
	const status: QueueStatusData["status"] =
		position === 0 ? "ADMITTED" : "WAITING";

	const now = new Date();
	const queueTokenExpiresAt = new Date(
		now.getTime() + initialTokenTtl * 1000,
	).toISOString();
	const admissionExpiresAt =
		status === "ADMITTED"
			? new Date(now.getTime() + 5 * 60 * 1000).toISOString() // 5분 고정 (Codex #2)
			: null;
	const eta = new Date(now.getTime() + position * 200).toISOString(); // mock: 1명당 0.2s

	return ok<QueueStatusData>({
		status,
		pollAfterMs: 5000,
		queueTokenExpiresAt,
		admissionExpiresAt,
		reservationId: status === "ADMITTED" ? "TR-2026-MOCK-001" : null,
		position,
		eta,
	});
}

// ─────────────────────────────────────────────────────────────────────────────
// payment start fixture (Toss 매핑)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Toss Payments status를 받아 내부 FSM state로 매핑한 payment/start 응답.
 * 학습 단계에서 PG 실 호출 X (Codex #8) — UI 시퀀스 시각화용.
 */
export function fixturePaymentStart(
	reservationId: string,
	tossStatus: TossStatus = "READY",
): ApiEnvelope<PaymentStartData> {
	const internal: ReservationState = TOSS_TO_INTERNAL[tossStatus];
	if (internal !== "PAYMENT_PENDING") {
		return err<PaymentStartData>(
			"PAYMENT_REJECTED",
			USER_STATE_LABEL[internal] ?? "결제를 시작할 수 없습니다.",
		);
	}
	return {
		code: "OK",
		message: USER_STATE_LABEL.PAYMENT_PENDING,
		idempotencyKey: `pay-${reservationId}-${Date.now()}`,
		serverTime: SERVER_NOW(),
		data: {
			reservationId,
			state: "PAYMENT_PENDING",
			tossStatus,
			redirectTo: `/payment/${reservationId}`,
		},
	};
}

// ─────────────────────────────────────────────────────────────────────────────
// 사용자 라벨 lookup (UI 헬퍼)
// ─────────────────────────────────────────────────────────────────────────────

export function userLabel(state: ReservationState): string {
	return USER_STATE_LABEL[state];
}
