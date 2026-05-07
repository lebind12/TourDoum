import { get, post } from "@/api/client";
import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { Attraction } from "./attractions";
import { useAttractionsStore } from "./attractions";

// ── BE API 응답 타입 ─────────────────────────────────────────────────────────
export interface FavoriteApiResponse {
	id: number;
	targetType: "ATTRACTION" | "ACCOMMODATION";
	targetId: number;
	createdAt: string;
}

export interface ToggleApiResponse {
	added: boolean;
}

// ── Store ────────────────────────────────────────────────────────────────────
export const useFavoritesStore = defineStore("favorites", () => {
	/** 즐겨찾기한 여행지(ATTRACTION) ID 집합 */
	const favoriteIds = ref<Set<number>>(new Set());
	const loading = ref(false);
	const error = ref<string | null>(null);

	const favoriteAttractions = computed<Attraction[]>(() => {
		const attractionsStore = useAttractionsStore();
		return attractionsStore.items.filter((a) => favoriteIds.value.has(a.id));
	});

	function isFavorite(attractionId: number): boolean {
		return favoriteIds.value.has(attractionId);
	}

	/**
	 * 즐겨찾기 목록 조회 — GET /api/favorites
	 * 인증 필수. 실패 시 error 세트 (silent fallback 금지).
	 * ATTRACTION 타입만 favoriteIds에 저장 (ACCOMMODATION은 별도 확장 예정).
	 */
	async function fetchFavorites(): Promise<void> {
		loading.value = true;
		error.value = null;

		const result = await get<FavoriteApiResponse[]>("/api/favorites");
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "즐겨찾기 목록을 불러오지 못했습니다.";
			return;
		}

		favoriteIds.value = new Set(
			result.data
				.filter((f) => f.targetType === "ATTRACTION")
				.map((f) => f.targetId),
		);
	}

	/**
	 * 즐겨찾기 토글 — POST /api/favorites/toggle
	 * 인증 필수. `added: true` → 추가, `added: false` → 제거.
	 * 실패 시 error 세트, 로컬 상태 변경 없음.
	 */
	async function toggleFavorite(attractionId: number): Promise<void> {
		loading.value = true;
		error.value = null;

		const result = await post<ToggleApiResponse>("/api/favorites/toggle", {
			targetType: "ATTRACTION",
			targetId: attractionId,
		});
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "즐겨찾기 토글에 실패했습니다.";
			return;
		}

		if (result.data.added) {
			favoriteIds.value = new Set([...favoriteIds.value, attractionId]);
		} else {
			const next = new Set(favoriteIds.value);
			next.delete(attractionId);
			favoriteIds.value = next;
		}
	}

	/** 즐겨찾기 추가. 이미 있으면 API 호출 생략. */
	async function addFavorite(attractionId: number): Promise<void> {
		if (isFavorite(attractionId)) return;
		await toggleFavorite(attractionId);
	}

	/** 즐겨찾기 제거. 없으면 API 호출 생략. */
	async function removeFavorite(attractionId: number): Promise<void> {
		if (!isFavorite(attractionId)) return;
		await toggleFavorite(attractionId);
	}

	return {
		favoriteIds,
		loading,
		error,
		favoriteAttractions,
		isFavorite,
		addFavorite,
		removeFavorite,
		toggleFavorite,
		fetchFavorites,
	};
});
