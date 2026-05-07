import { getAccessToken } from "@/api/auth-token";
import { post } from "@/api/client";
import { get } from "@/api/client";
import { defineStore } from "pinia";
import { ref } from "vue";

// ── 날짜 유틸 (뷰에서도 사용) ─────────────────────────────────────────────────
const MS_PER_DAY = 24 * 60 * 60 * 1000;
const YMD_RE = /^\d{4}-\d{2}-\d{2}$/;

function parseYmd(date: string): number | null {
	if (!YMD_RE.test(date)) return null;
	const [year, month, day] = date.split("-").map(Number);
	const utc = Date.UTC(year, month - 1, day);
	const parsed = new Date(utc);
	if (
		parsed.getUTCFullYear() !== year ||
		parsed.getUTCMonth() !== month - 1 ||
		parsed.getUTCDate() !== day
	)
		return null;
	return utc;
}

export function validateReservationDates(
	checkIn: string,
	checkOut: string,
): string | null {
	const checkInTime = parseYmd(checkIn);
	const checkOutTime = parseYmd(checkOut);
	if (checkInTime === null || checkOutTime === null)
		return "체크인과 체크아웃 날짜를 모두 선택해 주세요.";
	if (checkOutTime <= checkInTime)
		return "체크아웃은 체크인보다 늦어야 합니다.";
	return null;
}

export function calculateNights(checkIn: string, checkOut: string): number {
	const error = validateReservationDates(checkIn, checkOut);
	if (error) throw new Error(error);
	const ci = parseYmd(checkIn);
	const co = parseYmd(checkOut);
	if (ci === null || co === null) throw new Error("잘못된 날짜입니다.");
	return Math.round((co - ci) / MS_PER_DAY);
}

// ── FE 타입 ───────────────────────────────────────────────────────────────────
/** BE PaymentMethod enum (소문자 FE 표현) */
export type PaymentMethod = "card" | "kakaopay" | "toss";

/** 예약 위자드 중간 상태 */
export interface ReservationDraft {
	accommodationId: number;
	checkIn: string;
	checkOut: string;
	adults: number;
	children: number;
	paymentMethod: PaymentMethod;
}

/** FE 내부 예약 레코드 (BE ReservationResponse 매핑) */
export interface Reservation {
	id: string; // BE Long → string
	accommodationId: number;
	checkIn: string;
	checkOut: string;
	guests: number; // adults + children
	totalPrice: number;
	paymentMethod: PaymentMethod; // BE 대문자 → 소문자
	status: "confirmed" | "cancelled";
	createdAt: string;
}

// ── BE API 응답 타입 ─────────────────────────────────────────────────────────
export interface QuoteApiResponse {
	accommodationId: number;
	checkIn: string;
	checkOut: string;
	nights: number;
	guests: number;
	pricePerNight: number;
	cleaningFee: number;
	totalPrice: number;
}

export interface ReservationApiResponse {
	id: number;
	memberId: number;
	accommodationId: number;
	checkIn: string;
	checkOut: string;
	guests: number;
	totalPrice: number;
	paymentMethod: string; // 'CARD' | 'KAKAOPAY' | 'TOSS'
	status: string; // 'CONFIRMED' | 'CANCELLED'
	idempotencyKey: string;
	createdAt: string;
}

function mapApiToReservation(r: ReservationApiResponse): Reservation {
	return {
		id: r.id.toString(),
		accommodationId: r.accommodationId,
		checkIn: r.checkIn,
		checkOut: r.checkOut,
		guests: r.guests,
		totalPrice: r.totalPrice,
		paymentMethod: r.paymentMethod.toLowerCase() as PaymentMethod,
		status: r.status === "CANCELLED" ? "cancelled" : "confirmed",
		createdAt: r.createdAt,
	};
}

function isPaymentMethod(value: unknown): value is PaymentMethod {
	return value === "card" || value === "kakaopay" || value === "toss";
}

// ── Store ────────────────────────────────────────────────────────────────────
export const useReservationsStore = defineStore("reservations", () => {
	/** 예약 위자드 진행 중 임시 상태 */
	const current = ref<Partial<ReservationDraft> | null>(null);
	/** POST /api/reservations/quote 응답 (Step 1→2 전환 시 캐시) */
	const quote = ref<QuoteApiResponse | null>(null);
	/** 멱등성 키 — quote 단계에서 생성, confirm 헤더에 재사용 */
	const idempotencyKey = ref<string>("");
	/** 가장 최근 확정된 예약 (Step 3 영수증 표시용) */
	const lastConfirmed = ref<Reservation | null>(null);
	/** GET /api/reservations/me 결과 */
	const myReservations = ref<Reservation[]>([]);
	const loading = ref(false);
	const error = ref<string | null>(null);

	// ── 위자드 상태 관리 ───────────────────────────────────────────────────────
	function start(accommodationId: number) {
		if (!Number.isFinite(accommodationId))
			throw new Error("유효한 숙소 ID가 필요합니다.");
		current.value = { accommodationId };
		quote.value = null;
		idempotencyKey.value = "";
	}

	function setDates(payload: { checkIn: string; checkOut: string }) {
		if (!current.value) throw new Error("예약을 먼저 시작해 주세요.");
		const err = validateReservationDates(payload.checkIn, payload.checkOut);
		if (err) throw new Error(err);
		current.value = { ...current.value, ...payload };
	}

	function setGuests(payload: { adults: number; children: number }) {
		if (!current.value) throw new Error("예약을 먼저 시작해 주세요.");
		if (
			!Number.isInteger(payload.adults) ||
			!Number.isInteger(payload.children) ||
			payload.adults < 1 ||
			payload.children < 0
		) {
			throw new Error("인원은 성인 1명 이상으로 선택해 주세요.");
		}
		current.value = { ...current.value, ...payload };
	}

	function setPaymentMethod(method: PaymentMethod) {
		if (!current.value) throw new Error("예약을 먼저 시작해 주세요.");
		if (!isPaymentMethod(method))
			throw new Error("지원하지 않는 결제 수단입니다.");
		current.value = { ...current.value, paymentMethod: method };
	}

	// ── API 액션 ───────────────────────────────────────────────────────────────
	/**
	 * 가격 견적 조회 — POST /api/reservations/quote (인증 필수)
	 * Step 1 → 2 전환 시 호출. 성공 시 idempotencyKey 생성.
	 */
	async function fetchQuote(): Promise<QuoteApiResponse | null> {
		const draft = current.value;
		if (
			!draft?.accommodationId ||
			!draft.checkIn ||
			!draft.checkOut ||
			!draft.adults
		) {
			error.value = "예약 정보가 완성되지 않았습니다.";
			return null;
		}

		loading.value = true;
		error.value = null;

		const result = await post<QuoteApiResponse>("/api/reservations/quote", {
			accommodationId: draft.accommodationId,
			checkIn: draft.checkIn,
			checkOut: draft.checkOut,
			guests: (draft.adults ?? 1) + (draft.children ?? 0),
		});
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "견적 조회에 실패했습니다.";
			return null;
		}

		quote.value = result.data;
		// 멱등성 키는 견적 단계에서 생성 (재시도 시 동일 키 재사용)
		idempotencyKey.value = crypto.randomUUID();
		return result.data;
	}

	/**
	 * 예약 확정 — POST /api/reservations (인증 + Idempotency-Key 필수)
	 * 성공 시 `lastConfirmed` 갱신 + `myReservations` 앞에 추가.
	 */
	async function confirm(): Promise<Reservation | null> {
		const draft = current.value;
		if (
			!draft?.accommodationId ||
			!draft.checkIn ||
			!draft.checkOut ||
			!draft.adults ||
			!isPaymentMethod(draft.paymentMethod)
		) {
			error.value = "예약 정보가 완성되지 않았습니다.";
			return null;
		}

		if (!idempotencyKey.value) {
			error.value = "견적 조회를 먼저 진행해 주세요.";
			return null;
		}

		loading.value = true;
		error.value = null;

		// Idempotency-Key 헤더는 fetch 직접 사용 (api/client.post는 헤더 미지원)
		const BASE_URL =
			import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
		let data: ReservationApiResponse | null = null;
		let errMsg: string | null = null;

		try {
			// ADR-0011 FE-1 — direct fetch도 Bearer 자동 부착 정책에 합류.
			const headers: Record<string, string> = {
				"Content-Type": "application/json",
				"Idempotency-Key": idempotencyKey.value,
			};
			const token = getAccessToken();
			if (token) headers.Authorization = `Bearer ${token}`;

			const res = await fetch(`${BASE_URL}/api/reservations`, {
				method: "POST",
				headers,
				credentials: "include",
				body: JSON.stringify({
					accommodationId: draft.accommodationId,
					checkIn: draft.checkIn,
					checkOut: draft.checkOut,
					guests: (draft.adults ?? 1) + (draft.children ?? 0),
					paymentMethod: draft.paymentMethod.toUpperCase(),
				}),
			});
			if (!res.ok) {
				try {
					const body = await res.json();
					errMsg = body?.message ?? `HTTP ${res.status}`;
				} catch {
					errMsg = `HTTP ${res.status}`;
				}
			} else {
				data = (await res.json()) as ReservationApiResponse;
			}
		} catch (err) {
			errMsg = err instanceof Error ? err.message : "알 수 없는 오류";
		}

		loading.value = false;

		if (errMsg || !data) {
			error.value = errMsg ?? "예약 확정에 실패했습니다.";
			return null;
		}

		const mapped = mapApiToReservation(data);
		lastConfirmed.value = mapped;
		myReservations.value = [mapped, ...myReservations.value];
		current.value = null;
		return mapped;
	}

	/**
	 * 내 예약 목록 — GET /api/reservations/me
	 */
	async function fetchMyReservations(): Promise<void> {
		loading.value = true;
		error.value = null;

		const result = await get<ReservationApiResponse[]>("/api/reservations/me");
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "예약 목록을 불러오지 못했습니다.";
			return;
		}

		myReservations.value = result.data.map(mapApiToReservation);
	}

	/**
	 * 예약 취소 — POST /api/reservations/{id}/cancel
	 */
	async function cancelReservation(id: string): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await post<null>(`/api/reservations/${id}/cancel`, {});
		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		myReservations.value = myReservations.value.map((r) =>
			r.id === id ? { ...r, status: "cancelled" as const } : r,
		);
		return true;
	}

	return {
		current,
		quote,
		idempotencyKey,
		lastConfirmed,
		myReservations,
		loading,
		error,
		start,
		setDates,
		setGuests,
		setPaymentMethod,
		fetchQuote,
		confirm,
		fetchMyReservations,
		cancelReservation,
	};
});
