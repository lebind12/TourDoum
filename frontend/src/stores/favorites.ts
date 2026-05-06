import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { Attraction } from "./attractions";
import { useAttractionsStore } from "./attractions";

export const useFavoritesStore = defineStore("favorites", () => {
	/** 즐겨찾기한 여행지 ID 목록 (mockup: 로컬 상태) */
	const favoriteIds = ref<Set<number>>(new Set([1, 4, 7, 15]));
	const loading = ref(false);
	const error = ref<string | null>(null);

	const favoriteAttractions = computed<Attraction[]>(() => {
		const attractionsStore = useAttractionsStore();
		return attractionsStore.items.filter((a) => favoriteIds.value.has(a.id));
	});

	function isFavorite(attractionId: number): boolean {
		return favoriteIds.value.has(attractionId);
	}

	/** BE 연결 시 이 함수만 교체 */
	async function addFavorite(attractionId: number): Promise<void> {
		loading.value = true;
		error.value = null;
		await new Promise((r) => setTimeout(r, 80));
		favoriteIds.value.add(attractionId);
		loading.value = false;
	}

	/** BE 연결 시 이 함수만 교체 */
	async function removeFavorite(attractionId: number): Promise<void> {
		loading.value = true;
		error.value = null;
		await new Promise((r) => setTimeout(r, 80));
		favoriteIds.value.delete(attractionId);
		loading.value = false;
	}

	async function toggleFavorite(attractionId: number): Promise<void> {
		if (isFavorite(attractionId)) {
			await removeFavorite(attractionId);
		} else {
			await addFavorite(attractionId);
		}
	}

	/** BE 연결 시 이 함수만 교체 */
	async function fetchFavorites(): Promise<void> {
		loading.value = true;
		error.value = null;
		await new Promise((r) => setTimeout(r, 100));
		loading.value = false;
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
