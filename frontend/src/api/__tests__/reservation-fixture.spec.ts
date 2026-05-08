import {
	fixturePaymentStart,
	fixtureQueueStatus,
	userLabel,
} from "@/api/fixtures/reservation";
import {
	type ReservationState,
	STATE_COLOR_TOKEN,
	TOSS_TO_INTERNAL,
	type TossStatus,
	USER_STATE_LABEL,
} from "@/types/reservation";
/**
 * R11 — fixture handler 단위 테스트.
 * envelope 매핑 / Toss 상태 매핑 / 사용자 라벨 매핑의 single source of truth 보호.
 */
import { describe, expect, it } from "vitest";

describe("fixtureQueueStatus", () => {
	it("tick=0이면 WAITING이고 admissionExpiresAt이 null이다", () => {
		const env = fixtureQueueStatus({ tickSeconds: 0, initialPosition: 100 });
		expect(env.code).toBe("OK");
		expect(env.data?.status).toBe("WAITING");
		expect(env.data?.admissionExpiresAt).toBeNull();
		expect(env.data?.position).toBe(100);
		expect(env.data?.pollAfterMs).toBe(5000);
	});

	it("tick이 충분히 크면 ADMITTED로 전이하고 admissionExpiresAt이 set된다", () => {
		const env = fixtureQueueStatus({ tickSeconds: 30, initialPosition: 100 });
		expect(env.data?.status).toBe("ADMITTED");
		expect(env.data?.position).toBe(0);
		expect(env.data?.admissionExpiresAt).not.toBeNull();
		expect(env.data?.reservationId).not.toBeNull();
	});

	it("queue token TTL은 admission TTL과 분리된다 (Codex #2)", () => {
		const env = fixtureQueueStatus({
			tickSeconds: 30,
			initialPosition: 100,
			initialTokenTtlSeconds: 1800,
		});
		expect(env.data).not.toBeNull();
		expect(env.data?.admissionExpiresAt).not.toBeNull();
		const queueExpire = new Date(env.data?.queueTokenExpiresAt ?? 0).getTime();
		const admExpire = new Date(env.data?.admissionExpiresAt ?? 0).getTime();
		// admission(5분=300s) < queue(30분=1800s)
		expect(admExpire).toBeLessThan(queueExpire);
		// admission은 5분 ± 1초.
		const now = Date.now();
		expect(admExpire - now).toBeGreaterThan(295 * 1000);
		expect(admExpire - now).toBeLessThan(305 * 1000);
	});
});

describe("fixturePaymentStart", () => {
	it("Toss READY → 내부 PAYMENT_PENDING + redirectTo 박제", () => {
		const env = fixturePaymentStart("R-1", "READY");
		expect(env.code).toBe("OK");
		expect(env.data?.state).toBe("PAYMENT_PENDING");
		expect(env.data?.tossStatus).toBe("READY");
		expect(env.data?.redirectTo).toBe("/payment/R-1");
		expect(env.idempotencyKey).toMatch(/^pay-R-1-/);
	});

	it("Toss ABORTED는 PAYMENT_REJECTED envelope로 떨어진다", () => {
		const env = fixturePaymentStart("R-2", "ABORTED");
		expect(env.code).toBe("PAYMENT_REJECTED");
		expect(env.data).toBeNull();
	});

	it("Toss DONE은 시작 단계에서 거절 처리 (이미 결제 완료 상태)", () => {
		const env = fixturePaymentStart("R-3", "DONE");
		expect(env.code).toBe("PAYMENT_REJECTED");
	});
});

describe("Toss → 내부 FSM 매핑 표", () => {
	const cases: Array<[TossStatus, ReservationState]> = [
		["READY", "PAYMENT_PENDING"],
		["IN_PROGRESS", "PAYMENT_PENDING"],
		["DONE", "CAPTURED"],
		["ABORTED", "FAILED"],
		["EXPIRED", "FAILED"],
		["CANCELED", "CANCELLED"],
		["PARTIAL_CANCELED", "REFUND_PENDING"],
	];
	for (const [toss, internal] of cases) {
		it(`${toss} → ${internal}`, () => {
			expect(TOSS_TO_INTERNAL[toss]).toBe(internal);
		});
	}
});

describe("USER_STATE_LABEL — Codex #3 (내부 enum 비노출)", () => {
	it("모든 ReservationState가 한국어 라벨로 매핑된다", () => {
		const states = Object.keys(USER_STATE_LABEL) as ReservationState[];
		for (const s of states) {
			expect(USER_STATE_LABEL[s]).toMatch(/[가-힣]/);
		}
	});

	it("AUTHORIZED/CAPTURED/PAYMENT_PENDING은 동일 라벨('결제 중')로 묶여 사용자에게 단일 단계로 보인다", () => {
		expect(USER_STATE_LABEL.PAYMENT_PENDING).toBe("결제 중");
		expect(USER_STATE_LABEL.AUTHORIZED).toBe("결제 중");
		expect(USER_STATE_LABEL.CAPTURED).toBe("결제 중");
	});

	it("REJECTED/FAILED 동일 라벨, CANCELLED/REVERSED 동일 라벨", () => {
		expect(USER_STATE_LABEL.REJECTED).toBe(USER_STATE_LABEL.FAILED);
		expect(USER_STATE_LABEL.CANCELLED).toBe(USER_STATE_LABEL.REVERSED);
	});

	it("userLabel 헬퍼가 매핑 표를 그대로 반환한다", () => {
		expect(userLabel("CONFIRMED")).toBe("예약 확정");
		expect(userLabel("REFUND_PENDING")).toBe("환불 진행 중");
	});
});

describe("STATE_COLOR_TOKEN — semantic alias 우선 사용 (Codex #5)", () => {
	it("성공 계열은 --success를 가리킨다", () => {
		expect(STATE_COLOR_TOKEN.AUTHORIZED).toBe("--success");
		expect(STATE_COLOR_TOKEN.CAPTURED).toBe("--success");
		expect(STATE_COLOR_TOKEN.CONFIRMED).toBe("--success");
	});
	it("진행 계열은 --state-pending", () => {
		expect(STATE_COLOR_TOKEN.QUEUED).toBe("--state-pending");
		expect(STATE_COLOR_TOKEN.PAYMENT_PENDING).toBe("--state-pending");
	});
	it("실패 계열은 --state-failed", () => {
		expect(STATE_COLOR_TOKEN.REJECTED).toBe("--state-failed");
		expect(STATE_COLOR_TOKEN.FAILED).toBe("--state-failed");
	});
	it("환불/대기 경고 계열은 --warning, 환불 완료는 --state-neutral", () => {
		expect(STATE_COLOR_TOKEN.REFUND_PENDING).toBe("--warning");
		expect(STATE_COLOR_TOKEN.REFUNDED).toBe("--state-neutral");
	});
});
