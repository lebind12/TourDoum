/**
 * useReviewsStore — API 연결 단위 테스트
 *
 * api/client.ts의 get/post/del 함수를 vi.mock으로 교체.
 */
import { useReviewsStore } from "@/stores/reviews";
import type {
	ReviewApiResponse,
	ReviewPageResponse,
	ReviewSummaryResponse,
} from "@/stores/reviews";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/api/client", () => ({
	get: vi.fn(),
	post: vi.fn(),
	del: vi.fn(),
}));

import { del, get, post } from "@/api/client";
const mockGet = vi.mocked(get);
const mockPost = vi.mocked(post);
const mockDel = vi.mocked(del);

/** 테스트용 BE 응답 픽스처 */
const fixtureReview: ReviewApiResponse = {
	id: 1,
	memberId: 10,
	authorNickname: "테스터_김",
	targetType: "ATTRACTION",
	targetId: 42,
	rating: 5,
	title: "좋아요",
	content: "정말 멋진 곳이에요!",
	createdAt: "2026-05-07T10:00:00",
	updatedAt: "2026-05-07T10:00:00",
};

const fixtureReview2: ReviewApiResponse = {
	id: 2,
	memberId: 11,
	authorNickname: null, // null → "알 수 없음" fallback
	targetType: "ATTRACTION",
	targetId: 42,
	rating: 4,
	title: null,
	content: "괜찮아요",
	createdAt: "2026-05-06T09:00:00",
	updatedAt: "2026-05-06T09:00:00",
};

function makePageResponse(items: ReviewApiResponse[]): ReviewPageResponse {
	return {
		content: items,
		totalElements: items.length,
		page: 0,
		size: 20,
		totalPages: 1,
	};
}

describe("useReviewsStore — API 연결", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("초기 상태 — reviews 비어있음, error null", () => {
		const store = useReviewsStore();
		expect(store.reviews).toHaveLength(0);
		expect(store.error).toBeNull();
		expect(store.loading).toBe(false);
	});

	it("fetchByTarget — 성공 시 캐시에 매핑된 데이터 저장", async () => {
		mockGet.mockResolvedValueOnce({
			data: makePageResponse([fixtureReview, fixtureReview2]),
			error: null,
		});

		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);

		expect(mockGet).toHaveBeenCalledWith(
			expect.stringContaining("targetType=ATTRACTION"),
		);
		expect(mockGet).toHaveBeenCalledWith(
			expect.stringContaining("targetId=42"),
		);
		expect(store.reviews).toHaveLength(2);
		expect(store.error).toBeNull();

		// 필드 매핑 확인
		const first = store.getByTarget("attraction", 42)[0]; // createdAt 내림차순
		expect(first.id).toBe("1"); // Long → string
		expect(first.targetType).toBe("attraction"); // 소문자 변환
		expect(first.comment).toBe("정말 멋진 곳이에요!"); // content → comment
		expect(first.authorNickname).toBe("테스터_김"); // BE authorNickname 필드
	});

	it("fetchByTarget — API 실패 시 error 세트", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 500" });

		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);

		expect(store.error).toBe("HTTP 500");
		expect(store.reviews).toHaveLength(0);
	});

	it("getByTarget — createdAt 내림차순 정렬", async () => {
		mockGet.mockResolvedValueOnce({
			data: makePageResponse([fixtureReview, fixtureReview2]),
			error: null,
		});
		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);

		const items = store.getByTarget("attraction", 42);
		expect(items).toHaveLength(2);
		expect(new Date(items[0].createdAt).getTime()).toBeGreaterThanOrEqual(
			new Date(items[1].createdAt).getTime(),
		);
	});

	it("getByTarget — 다른 대상의 리뷰는 포함되지 않음", async () => {
		const otherReview: ReviewApiResponse = {
			...fixtureReview,
			id: 99,
			targetId: 999,
		};
		mockGet
			.mockResolvedValueOnce({
				data: makePageResponse([fixtureReview]),
				error: null,
			})
			.mockResolvedValueOnce({
				data: makePageResponse([otherReview]),
				error: null,
			});

		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);
		await store.fetchByTarget("attraction", 999);

		expect(store.getByTarget("attraction", 42)).toHaveLength(1);
		expect(store.getByTarget("attraction", 999)).toHaveLength(1);
	});

	it("averageRating — 리뷰 없는 대상은 0 반환", () => {
		const store = useReviewsStore();
		expect(store.averageRating("attraction", 9999)).toBe(0);
	});

	it("averageRating — 캐시 기준 평균 계산", async () => {
		mockGet.mockResolvedValueOnce({
			data: makePageResponse([fixtureReview, fixtureReview2]),
			error: null,
		});
		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);

		const avg = store.averageRating("attraction", 42);
		expect(avg).toBeCloseTo(4.5, 1); // (5 + 4) / 2
	});

	it("fetchSummary — 성공 시 summaries 캐시 갱신", async () => {
		const summary: ReviewSummaryResponse = { avgRating: 4.8, count: 15 };
		mockGet.mockResolvedValueOnce({ data: summary, error: null });

		const store = useReviewsStore();
		const result = await store.fetchSummary("attraction", 42);

		expect(result).toEqual(summary);
		// summaries 캐시 반영 → averageRating이 summary를 우선 사용
		expect(store.averageRating("attraction", 42)).toBe(4.8);
	});

	it("addReview — 성공 시 캐시에 추가, Review 반환 (BE authorNickname 사용)", async () => {
		mockPost.mockResolvedValueOnce({ data: fixtureReview, error: null });

		const store = useReviewsStore();
		const result = await store.addReview(
			"attraction",
			42,
			5,
			"정말 멋진 곳이에요!",
		);

		expect(mockPost).toHaveBeenCalledWith("/api/reviews", {
			targetType: "ATTRACTION",
			targetId: 42,
			rating: 5,
			content: "정말 멋진 곳이에요!", // comment → content
		});
		expect(result).not.toBeNull();
		expect(result?.id).toBe("1");
		expect(result?.authorNickname).toBe("테스터_김"); // BE authorNickname 사용
		expect(result?.comment).toBe("정말 멋진 곳이에요!");
		expect(store.reviews).toHaveLength(1);
		expect(store.reviews[0].id).toBe("1"); // 맨 앞에 추가
	});

	it("addReview — API 실패 시 null 반환, error 세트", async () => {
		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 401" });

		const store = useReviewsStore();
		const result = await store.addReview("attraction", 42, 3, "보통이에요");

		expect(result).toBeNull();
		expect(store.error).toBe("HTTP 401");
		expect(store.reviews).toHaveLength(0);
	});

	it("deleteReview — 성공 시 캐시에서 제거, true 반환", async () => {
		// 먼저 fetchByTarget으로 캐시 채우기
		mockGet.mockResolvedValueOnce({
			data: makePageResponse([fixtureReview, fixtureReview2]),
			error: null,
		});
		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);
		expect(store.reviews).toHaveLength(2);

		mockDel.mockResolvedValueOnce({ data: null, error: null });
		const success = await store.deleteReview("1");

		expect(mockDel).toHaveBeenCalledWith("/api/reviews/1");
		expect(success).toBe(true);
		expect(store.reviews).toHaveLength(1);
		expect(store.reviews.find((r) => r.id === "1")).toBeUndefined();
	});

	it("deleteReview — API 실패 시 false 반환, 캐시 유지", async () => {
		mockGet.mockResolvedValueOnce({
			data: makePageResponse([fixtureReview]),
			error: null,
		});
		const store = useReviewsStore();
		await store.fetchByTarget("attraction", 42);

		mockDel.mockResolvedValueOnce({ data: null, error: "HTTP 403" });
		const success = await store.deleteReview("1");

		expect(success).toBe(false);
		expect(store.error).toBe("HTTP 403");
		expect(store.reviews).toHaveLength(1); // 캐시 유지
	});
});
