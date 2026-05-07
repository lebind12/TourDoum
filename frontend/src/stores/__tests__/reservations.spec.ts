/**
 * useReservationsStore — API 연결 단위 테스트
 *
 * api/client.ts의 get/post 함수 및 globalThis.fetch(confirm 전용)를 vi.mock으로 교체.
 */
import {
	useReservationsStore,
	validateReservationDates,
} from "@/stores/reservations";
import type {
	QuoteApiResponse,
	ReservationApiResponse,
} from "@/stores/reservations";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/api/client", () => ({
	get: vi.fn(),
	post: vi.fn(),
	del: vi.fn(),
}));

import { get, post } from "@/api/client";
const mockGet = vi.mocked(get);
const mockPost = vi.mocked(post);

/** fetch mock (confirm에서 Idempotency-Key 헤더를 추가하기 위해 직접 사용) */
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);
vi.stubGlobal("crypto", { randomUUID: () => "test-uuid-1234" });

const quoteFixture: QuoteApiResponse = {
	accommodationId: 1,
	checkIn: "2026-06-01",
	checkOut: "2026-06-03",
	nights: 2,
	guests: 2,
	pricePerNight: 150000,
	cleaningFee: 20000,
	totalPrice: 320000,
};

const reservationFixture: ReservationApiResponse = {
	id: 101,
	memberId: 10,
	accommodationId: 1,
	checkIn: "2026-06-01",
	checkOut: "2026-06-03",
	guests: 2,
	totalPrice: 320000,
	paymentMethod: "CARD",
	status: "CONFIRMED",
	idempotencyKey: "test-uuid-1234",
	createdAt: "2026-05-07T10:00:00",
};

describe("유틸 함수", () => {
	it("validateReservationDates — 유효한 날짜는 null 반환", () => {
		expect(validateReservationDates("2026-06-01", "2026-06-03")).toBeNull();
	});

	it("validateReservationDates — checkOut이 checkIn보다 이르면 에러 메시지", () => {
		expect(validateReservationDates("2026-06-03", "2026-06-01")).toBeTruthy();
	});

	it("validateReservationDates — 잘못된 형식이면 에러 메시지", () => {
		expect(validateReservationDates("2026/06/01", "2026/06/03")).toBeTruthy();
	});
});

describe("useReservationsStore — 위자드 상태 관리", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	it("초기 상태", () => {
		const store = useReservationsStore();
		expect(store.current).toBeNull();
		expect(store.myReservations).toHaveLength(0);
		expect(store.lastConfirmed).toBeNull();
	});

	it("start → current 초기화", () => {
		const store = useReservationsStore();
		store.start(1);
		expect(store.current?.accommodationId).toBe(1);
	});

	it("setDates — 유효한 날짜 저장", () => {
		const store = useReservationsStore();
		store.start(1);
		store.setDates({ checkIn: "2026-06-01", checkOut: "2026-06-03" });
		expect(store.current?.checkIn).toBe("2026-06-01");
	});

	it("setDates — 유효하지 않은 날짜는 throw", () => {
		const store = useReservationsStore();
		store.start(1);
		expect(() =>
			store.setDates({ checkIn: "2026-06-03", checkOut: "2026-06-01" }),
		).toThrow();
	});

	it("setGuests — 유효한 인원 저장", () => {
		const store = useReservationsStore();
		store.start(1);
		store.setGuests({ adults: 2, children: 1 });
		expect(store.current?.adults).toBe(2);
		expect(store.current?.children).toBe(1);
	});

	it("setPaymentMethod — 유효한 결제수단 저장 (kakaopay/toss)", () => {
		const store = useReservationsStore();
		store.start(1);
		store.setPaymentMethod("kakaopay");
		expect(store.current?.paymentMethod).toBe("kakaopay");
		store.setPaymentMethod("toss");
		expect(store.current?.paymentMethod).toBe("toss");
	});
});

describe("useReservationsStore — API 연결", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("fetchQuote — 성공 시 quote 저장 + idempotencyKey 생성", async () => {
		mockPost.mockResolvedValueOnce({ data: quoteFixture, error: null });

		const store = useReservationsStore();
		store.start(1);
		store.setDates({ checkIn: "2026-06-01", checkOut: "2026-06-03" });
		store.setGuests({ adults: 2, children: 0 });

		const result = await store.fetchQuote();

		expect(mockPost).toHaveBeenCalledWith(
			"/api/reservations/quote",
			expect.objectContaining({
				accommodationId: 1,
				checkIn: "2026-06-01",
				guests: 2,
			}),
		);
		expect(result).toEqual(quoteFixture);
		expect(store.quote?.cleaningFee).toBe(20000);
		expect(store.idempotencyKey).toBe("test-uuid-1234");
	});

	it("fetchQuote — 실패 시 null 반환, error 세트", async () => {
		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 401" });

		const store = useReservationsStore();
		store.start(1);
		store.setDates({ checkIn: "2026-06-01", checkOut: "2026-06-03" });
		store.setGuests({ adults: 1, children: 0 });

		const result = await store.fetchQuote();
		expect(result).toBeNull();
		expect(store.error).toBe("HTTP 401");
	});

	it("confirm — 성공 시 lastConfirmed + myReservations 갱신, Idempotency-Key 헤더 전송", async () => {
		mockPost.mockResolvedValueOnce({ data: quoteFixture, error: null });

		const store = useReservationsStore();
		store.start(1);
		store.setDates({ checkIn: "2026-06-01", checkOut: "2026-06-03" });
		store.setGuests({ adults: 2, children: 0 });
		store.setPaymentMethod("card");
		await store.fetchQuote();

		mockFetch.mockResolvedValueOnce({
			ok: true,
			json: async () => reservationFixture,
		});

		const result = await store.confirm();

		expect(mockFetch).toHaveBeenCalledWith(
			expect.stringContaining("/api/reservations"),
			expect.objectContaining({
				headers: expect.objectContaining({
					"Idempotency-Key": "test-uuid-1234",
				}),
			}),
		);
		expect(result).not.toBeNull();
		expect(result?.id).toBe("101"); // Long → string
		expect(result?.paymentMethod).toBe("card"); // CARD → card
		expect(result?.status).toBe("confirmed"); // CONFIRMED → confirmed
		expect(store.lastConfirmed?.id).toBe("101");
		expect(store.myReservations).toHaveLength(1);
		expect(store.current).toBeNull(); // 위자드 초기화
	});

	it("confirm — API 실패 시 null, error 세트, 위자드 상태 유지", async () => {
		mockPost.mockResolvedValueOnce({ data: quoteFixture, error: null });

		const store = useReservationsStore();
		store.start(1);
		store.setDates({ checkIn: "2026-06-01", checkOut: "2026-06-03" });
		store.setGuests({ adults: 1, children: 0 });
		store.setPaymentMethod("toss");
		await store.fetchQuote();

		mockFetch.mockResolvedValueOnce({
			ok: false,
			json: async () => ({ message: "HTTP 409" }),
		});

		const result = await store.confirm();
		expect(result).toBeNull();
		expect(store.error).toBe("HTTP 409");
	});

	it("fetchMyReservations — 성공 시 myReservations 갱신", async () => {
		mockGet.mockResolvedValueOnce({
			data: [reservationFixture],
			error: null,
		});

		const store = useReservationsStore();
		await store.fetchMyReservations();

		expect(mockGet).toHaveBeenCalledWith("/api/reservations/me");
		expect(store.myReservations).toHaveLength(1);
		expect(store.myReservations[0].id).toBe("101");
		expect(store.myReservations[0].paymentMethod).toBe("card");
		expect(store.myReservations[0].guests).toBe(2);
	});

	it("cancelReservation — 성공 시 status → cancelled", async () => {
		mockGet.mockResolvedValueOnce({ data: [reservationFixture], error: null });
		const store = useReservationsStore();
		await store.fetchMyReservations();

		mockPost.mockResolvedValueOnce({ data: null, error: null });
		const success = await store.cancelReservation("101");

		expect(success).toBe(true);
		expect(store.myReservations[0].status).toBe("cancelled");
	});

	it("cancelReservation — 실패 시 false, 상태 유지", async () => {
		mockGet.mockResolvedValueOnce({ data: [reservationFixture], error: null });
		const store = useReservationsStore();
		await store.fetchMyReservations();

		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 403" });
		const success = await store.cancelReservation("101");

		expect(success).toBe(false);
		expect(store.error).toBe("HTTP 403");
		expect(store.myReservations[0].status).toBe("confirmed");
	});
});
