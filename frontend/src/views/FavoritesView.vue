<template>
  <main class="min-h-screen bg-slate-50">
    <div class="max-w-4xl mx-auto px-4 py-8">
      <!-- 헤더 -->
      <div class="mb-6">
        <h1 class="text-3xl font-bold text-slate-900">즐겨찾기</h1>
        <p class="text-slate-500 mt-1">내가 저장한 여행지 {{ favoritesStore.favoriteAttractions.length }}곳</p>
      </div>

      <!-- 비어있을 때 -->
      <template v-if="favoritesStore.favoriteAttractions.length === 0">
        <div class="bg-white rounded-xl border border-slate-200 p-16 text-center">
          <svg class="w-16 h-16 mx-auto text-slate-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
          </svg>
          <p class="text-slate-500 text-lg font-medium">아직 즐겨찾기한 여행지가 없어요</p>
          <p class="text-slate-400 text-sm mt-2">여행지를 탐색하고 마음에 드는 곳을 저장해보세요</p>
          <RouterLink
            to="/attractions"
            class="mt-6 inline-block bg-sky-500 text-white px-6 py-2 rounded-lg font-medium hover:bg-sky-600 transition-colors text-sm"
          >
            여행지 탐색하기
          </RouterLink>
        </div>
      </template>

      <!-- 즐겨찾기 목록 -->
      <template v-else>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="attraction in favoritesStore.favoriteAttractions"
            :key="attraction.id"
            class="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm"
          >
            <div class="relative">
              <RouterLink :to="{ name: 'attraction-detail', params: { id: attraction.id } }">
                <img
                  :src="attraction.imageUrl"
                  :alt="attraction.name"
                  class="w-full h-44 object-cover"
                  loading="lazy"
                />
              </RouterLink>
              <!-- 삭제 버튼 -->
              <button
                type="button"
                class="absolute top-3 right-3 bg-white rounded-full p-1.5 shadow hover:bg-red-50 transition-colors"
                :aria-label="`${attraction.name} 즐겨찾기 제거`"
                @click="handleRemove(attraction.id)"
              >
                <svg class="w-5 h-5 text-red-500" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
              </button>
            </div>
            <RouterLink
              :to="{ name: 'attraction-detail', params: { id: attraction.id } }"
              class="block p-4"
            >
              <div class="flex items-start justify-between">
                <h2 class="font-semibold text-slate-900">{{ attraction.name }}</h2>
                <span class="text-xs bg-sky-100 text-sky-700 rounded-full px-2 py-0.5 font-medium shrink-0">
                  {{ attraction.category }}
                </span>
              </div>
              <p class="text-xs text-slate-500 mt-1">{{ attraction.address }}</p>
              <div class="flex items-center gap-2 mt-2">
                <span class="text-amber-500 text-sm">★ {{ attraction.rating.toFixed(1) }}</span>
                <span class="text-slate-400 text-xs">({{ attraction.reviewCount.toLocaleString() }})</span>
              </div>
            </RouterLink>
          </div>
        </div>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { useAttractionsStore } from "@/stores/attractions";
import { useFavoritesStore } from "@/stores/favorites";
import { onMounted } from "vue";
import { RouterLink } from "vue-router";

const favoritesStore = useFavoritesStore();
const attractionsStore = useAttractionsStore();

async function handleRemove(id: number) {
	await favoritesStore.removeFavorite(id);
}

onMounted(async () => {
	await Promise.all([
		attractionsStore.fetchAttractions(),
		favoritesStore.fetchFavorites(),
	]);
});
</script>
