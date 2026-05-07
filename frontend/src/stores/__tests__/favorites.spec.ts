/**
 * useFavoritesStore — API 연결 단위 테스트
 *
 * api/client.ts의 `get`/`post` 함수를 vi.mock으로 교체.
 * attractions 스토어도 mock하여 favoriteAttractions computed 의존성 격리.
 */
import { useFavoritesStore } from "@/stores/favorites";
import type {
	FavoriteApiResponse,
	ToggleApiResponse,
} from "@/stores/favorites";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// api/client 모킹
vi.mock("@/api/client", () => ({
	get: vi.fn(),
	post: vi.fn(),
}));

// attractions 스토어 모킹 (favoriteAttractions computed 격리)
vi.mock("@/stores/attractions", () => ({
	useAttractionsStore: vi.fn(() => ({ items: [] })),
}));

import { get, post } from "@/api/client";
const mockGet = vi.mocked(get);
const mockPost = vi.mocked(post);

/** BE 응답 픽스처 — ATTRACTION 2개 + ACCOMMODATION 1개 */
const fixtureFavorites: FavoriteApiResponse[] = [
	{
		id: 1,
		targetType: "ATTRACTION",
		targetId: 42,
		createdAt: "2026-05-07T00:00:00",
	},
	{
		id: 2,
		targetType: "ACCOMMODATION",
		targetId: 7,
		createdAt: "2026-05-07T00:01:00",
	},
	{
		id: 3,
		targetType: "ATTRACTION",
		targetId: 15,
		createdAt: "2026-05-07T00:02:00",
	},
];

describe("useFavoritesStore — API 연결", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("초기 상태 — favoriteIds 비어있음, error null", () => {
		const store = useFavoritesStore();
		expect(store.favoriteIds.size).toBe(0);
		expect(store.error).toBeNull();
		expect(store.loading).toBe(false);
	});

	it("fetchFavorites — 성공 시 ATTRACTION targetId만 favoriteIds에 저장", async () => {
		mockGet.mockResolvedValueOnce({ data: fixtureFavorites, error: null });

		const store = useFavoritesStore();
		await store.fetchFavorites();

		expect(mockGet).toHaveBeenCalledWith("/api/favorites");
		expect(store.favoriteIds.size).toBe(2);
		expect(store.favoriteIds.has(42)).toBe(true);
		expect(store.favoriteIds.has(15)).toBe(true);
		expect(store.favoriteIds.has(7)).toBe(false); // ACCOMMODATION 제외
		expect(store.error).toBeNull();
	});

	it("fetchFavorites — API 실패 시 error 세트, favoriteIds 유지", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 401" });

		const store = useFavoritesStore();
		await store.fetchFavorites();

		expect(store.error).toBe("HTTP 401");
		expect(store.favoriteIds.size).toBe(0);
	});

	it("isFavorite — fetchFavorites 후 정확히 동작", async () => {
		mockGet.mockResolvedValueOnce({ data: fixtureFavorites, error: null });
		const store = useFavoritesStore();
		await store.fetchFavorites();

		expect(store.isFavorite(42)).toBe(true);
		expect(store.isFavorite(99)).toBe(false);
	});

	it("toggleFavorite — added=true 시 favoriteIds에 추가", async () => {
		const res: ToggleApiResponse = { added: true };
		mockPost.mockResolvedValueOnce({ data: res, error: null });

		const store = useFavoritesStore();
		await store.toggleFavorite(42);

		expect(mockPost).toHaveBeenCalledWith("/api/favorites/toggle", {
			targetType: "ATTRACTION",
			targetId: 42,
		});
		expect(store.favoriteIds.has(42)).toBe(true);
		expect(store.error).toBeNull();
	});

	it("toggleFavorite — added=false 시 favoriteIds에서 삭제", async () => {
		// 먼저 목록 로드
		mockGet.mockResolvedValueOnce({
			data: [{ id: 1, targetType: "ATTRACTION", targetId: 42, createdAt: "" }],
			error: null,
		});
		const store = useFavoritesStore();
		await store.fetchFavorites();
		expect(store.favoriteIds.has(42)).toBe(true);

		// 제거
		mockPost.mockResolvedValueOnce({ data: { added: false }, error: null });
		await store.toggleFavorite(42);

		expect(store.favoriteIds.has(42)).toBe(false);
	});

	it("toggleFavorite — API 실패 시 error 세트, 로컬 상태 변경 없음", async () => {
		mockGet.mockResolvedValueOnce({
			data: [{ id: 1, targetType: "ATTRACTION", targetId: 42, createdAt: "" }],
			error: null,
		});
		const store = useFavoritesStore();
		await store.fetchFavorites();

		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 500" });
		await store.toggleFavorite(42);

		expect(store.error).toBe("HTTP 500");
		expect(store.favoriteIds.has(42)).toBe(true); // 상태 유지
	});

	it("addFavorite — 이미 즐겨찾기된 경우 API 호출 없음", async () => {
		mockGet.mockResolvedValueOnce({
			data: [{ id: 1, targetType: "ATTRACTION", targetId: 42, createdAt: "" }],
			error: null,
		});
		const store = useFavoritesStore();
		await store.fetchFavorites();

		await store.addFavorite(42); // 이미 있음

		expect(mockPost).not.toHaveBeenCalled();
	});

	it("addFavorite — 없으면 toggleFavorite 호출", async () => {
		mockPost.mockResolvedValueOnce({ data: { added: true }, error: null });

		const store = useFavoritesStore();
		await store.addFavorite(99);

		expect(mockPost).toHaveBeenCalledWith("/api/favorites/toggle", {
			targetType: "ATTRACTION",
			targetId: 99,
		});
		expect(store.favoriteIds.has(99)).toBe(true);
	});

	it("removeFavorite — 즐겨찾기 아닌 경우 API 호출 없음", async () => {
		const store = useFavoritesStore();
		await store.removeFavorite(999);

		expect(mockPost).not.toHaveBeenCalled();
	});

	it("removeFavorite — 있으면 toggleFavorite 호출해 제거", async () => {
		mockGet.mockResolvedValueOnce({
			data: [{ id: 1, targetType: "ATTRACTION", targetId: 42, createdAt: "" }],
			error: null,
		});
		const store = useFavoritesStore();
		await store.fetchFavorites();

		mockPost.mockResolvedValueOnce({ data: { added: false }, error: null });
		await store.removeFavorite(42);

		expect(store.favoriteIds.has(42)).toBe(false);
	});
});
