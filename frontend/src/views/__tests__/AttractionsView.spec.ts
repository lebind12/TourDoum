import { useAttractionsStore } from "@/stores/attractions";
import { useFavoritesStore } from "@/stores/favorites";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";

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

describe("FavoritesStore (mockup)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	it("기본 즐겨찾기 4개가 있다", () => {
		const _attractionsStore = useAttractionsStore(); // side-effect: seed data
		const store = useFavoritesStore();
		expect(store.favoriteIds.size).toBe(4);
	});

	it("isFavorite 이 올바르게 동작한다", () => {
		useAttractionsStore();
		const store = useFavoritesStore();
		expect(store.isFavorite(1)).toBe(true);
		expect(store.isFavorite(999)).toBe(false);
	});

	it("addFavorite / removeFavorite 이 동작한다", async () => {
		useAttractionsStore();
		const store = useFavoritesStore();
		await store.addFavorite(10);
		expect(store.isFavorite(10)).toBe(true);
		await store.removeFavorite(10);
		expect(store.isFavorite(10)).toBe(false);
	});
});
