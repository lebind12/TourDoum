import { REVIEWS_STORAGE_KEY, useReviewsStore } from "@/stores/reviews";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const localStorageMock = (() => {
	let store: Record<string, string> = {};
	return {
		getItem: vi.fn((key: string) => store[key] ?? null),
		setItem: vi.fn((key: string, value: string) => {
			store[key] = value;
		}),
		removeItem: vi.fn((key: string) => {
			delete store[key];
		}),
		clear: vi.fn(() => {
			store = {};
		}),
	};
})();

vi.stubGlobal("localStorage", localStorageMock);

describe("useReviewsStore", () => {
	beforeEach(() => {
		localStorageMock.clear();
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("시드 데이터가 로드된다 (30건 이상)", () => {
		const store = useReviewsStore();
		expect(store.reviews.length).toBeGreaterThanOrEqual(30);
	});

	it("getByTarget — 여행지 1번 후기를 필터링한다", () => {
		const store = useReviewsStore();
		const items = store.getByTarget("attraction", 1);
		expect(items.length).toBeGreaterThan(0);
		for (const r of items) {
			expect(r.targetType).toBe("attraction");
			expect(r.targetId).toBe(1);
		}
	});

	it("getByTarget — 결과는 createdAt 내림차순이다", () => {
		const store = useReviewsStore();
		const items = store.getByTarget("attraction", 1);
		for (let i = 0; i < items.length - 1; i++) {
			expect(new Date(items[i].createdAt).getTime()).toBeGreaterThanOrEqual(
				new Date(items[i + 1].createdAt).getTime(),
			);
		}
	});

	it("averageRating — 리뷰 없는 대상은 0을 반환한다", () => {
		const store = useReviewsStore();
		// 존재하지 않는 targetId
		expect(store.averageRating("attraction", 9999)).toBe(0);
	});

	it("averageRating — 1~5 범위의 합산 평균을 반환한다", () => {
		const store = useReviewsStore();
		const avg = store.averageRating("attraction", 1);
		expect(avg).toBeGreaterThanOrEqual(1);
		expect(avg).toBeLessThanOrEqual(5);
	});

	it("addReview — 새 후기를 추가한다", () => {
		const store = useReviewsStore();
		const before = store.reviews.length;
		const review = store.addReview(
			"attraction",
			999,
			"테스터_김",
			4,
			"좋은 곳이에요!",
		);
		expect(store.reviews.length).toBe(before + 1);
		expect(review.authorNickname).toBe("테스터_김");
		expect(review.rating).toBe(4);
		expect(review.targetId).toBe(999);
	});

	it("addReview — 가장 앞(최신)에 추가된다", () => {
		const store = useReviewsStore();
		const review = store.addReview(
			"accommodation",
			1,
			"최신_사용자",
			5,
			"최고!",
		);
		expect(store.reviews[0].id).toBe(review.id);
	});

	it("updateReview — 기존 후기를 수정한다", () => {
		const store = useReviewsStore();
		const review = store.addReview("attraction", 2, "수정자", 3, "그냥 그래요");
		const success = store.updateReview(review.id, 5, "다시 보니 최고예요!");
		expect(success).toBe(true);
		const updated = store.reviews.find((r) => r.id === review.id);
		if (!updated) throw new Error("review not found after update");
		expect(updated.rating).toBe(5);
		expect(updated.comment).toBe("다시 보니 최고예요!");
	});

	it("updateReview — 없는 id면 false를 반환한다", () => {
		const store = useReviewsStore();
		expect(store.updateReview("nonexistent", 5, "텍스트")).toBe(false);
	});

	it("deleteReview — 후기를 삭제한다", () => {
		const store = useReviewsStore();
		const review = store.addReview("accommodation", 2, "삭제자", 2, "별로예요");
		const before = store.reviews.length;
		const success = store.deleteReview(review.id);
		expect(success).toBe(true);
		expect(store.reviews.length).toBe(before - 1);
		expect(store.reviews.find((r) => r.id === review.id)).toBeUndefined();
	});

	it("deleteReview — 없는 id면 false를 반환한다", () => {
		const store = useReviewsStore();
		expect(store.deleteReview("nonexistent")).toBe(false);
	});

	it("addReview 후 localStorage에 저장된다", async () => {
		const store = useReviewsStore();
		store.addReview("attraction", 1, "스토리지_테스터", 5, "잘 저장되나요?");
		await new Promise((r) => setTimeout(r, 0));
		expect(localStorageMock.setItem).toHaveBeenCalledWith(
			REVIEWS_STORAGE_KEY,
			expect.any(String),
		);
	});
});
