import { useAttractionsStore } from "@/stores/attractions";
import { useFavoritesStore } from "@/stores/favorites";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

// favorites.ts가 api/client를 사용하므로 mock 필요
vi.mock("@/api/client", () => ({
	get: vi.fn(),
	post: vi.fn(),
}));

import { post } from "@/api/client";
const mockPost = vi.mocked(post);

describe("AttractionsStore (mockup)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	it("초기 로드 시 50개 여행지가 있다", () => {
		const store = useAttractionsStore();
		expect(store.items).toHaveLength(50);
	});

	it("카테고리 필터가 동작한다", () => {
		const store = useAttractionsStore();
		store.setCategory("문화재");
		expect(store.filtered.every((a) => a.category === "문화재")).toBe(true);
	});

	it("지역 필터가 동작한다", () => {
		const store = useAttractionsStore();
		store.setSido("제주");
		expect(store.filtered.every((a) => a.sido === "제주")).toBe(true);
		expect(store.filtered.length).toBeGreaterThan(0);
	});

	it("검색어 필터가 동작한다", () => {
		const store = useAttractionsStore();
		store.setSearch("경복궁");
		expect(store.filtered.some((a) => a.name.includes("경복궁"))).toBe(true);
	});

	it("getById 가 올바른 여행지를 반환한다", () => {
		const store = useAttractionsStore();
		const attraction = store.getById(1);
		expect(attraction).toBeDefined();
		expect(attraction?.name).toBe("경복궁");
	});

	it("없는 ID 에 대해 undefined 를 반환한다", () => {
		const store = useAttractionsStore();
		expect(store.getById(9999)).toBeUndefined();
	});
});

describe("FavoritesStore — API 연결 후", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	it("초기 상태 — favoriteIds 비어있음 (mock 시드 없음)", () => {
		const store = useFavoritesStore();
		expect(store.favoriteIds.size).toBe(0);
	});

	it("isFavorite — 기본 false, toggleFavorite 후 true", async () => {
		mockPost.mockResolvedValueOnce({ data: { added: true }, error: null });
		const store = useFavoritesStore();
		expect(store.isFavorite(1)).toBe(false);
		await store.toggleFavorite(1);
		expect(store.isFavorite(1)).toBe(true);
		expect(store.isFavorite(999)).toBe(false);
	});

	it("addFavorite / removeFavorite 이 동작한다", async () => {
		mockPost
			.mockResolvedValueOnce({ data: { added: true }, error: null }) // addFavorite
			.mockResolvedValueOnce({ data: { added: false }, error: null }); // removeFavorite

		const store = useFavoritesStore();
		await store.addFavorite(10);
		expect(store.isFavorite(10)).toBe(true);
		await store.removeFavorite(10);
		expect(store.isFavorite(10)).toBe(false);
	});
});
