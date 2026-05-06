<template>
  <main class="min-h-screen bg-slate-50">
    <template v-if="!attraction">
      <div class="max-w-3xl mx-auto px-4 py-20 text-center">
        <p class="text-slate-400 text-lg">여행지를 찾을 수 없습니다.</p>
        <RouterLink to="/attractions" class="mt-4 inline-block text-sky-600 hover:underline text-sm">
          목록으로 돌아가기
        </RouterLink>
      </div>
    </template>

    <template v-else>
      <!-- 히어로 이미지 -->
      <div class="relative h-64 sm:h-80 overflow-hidden">
        <img
          :src="attraction.imageUrl"
          :alt="attraction.name"
          class="w-full h-full object-cover"
        />
        <div class="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
        <div class="absolute bottom-0 left-0 right-0 px-6 pb-6">
          <span class="text-xs bg-sky-500 text-white rounded-full px-3 py-1 font-medium">
            {{ attraction.category }}
          </span>
          <h1 class="text-3xl font-bold text-white mt-2">{{ attraction.name }}</h1>
          <p class="text-white/80 text-sm mt-1">{{ attraction.address }}</p>
        </div>
        <!-- 뒤로가기 버튼 -->
        <button
          type="button"
          class="absolute top-4 left-4 bg-white/20 backdrop-blur-sm text-white rounded-full p-2 hover:bg-white/30 transition-colors"
          @click="$router.back()"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <!-- 즐겨찾기 버튼 -->
        <div class="absolute top-4 right-4">
          <FavoriteButton :attraction-id="attraction.id" size="lg" />
        </div>
      </div>

      <!-- 상세 정보 -->
      <div class="max-w-3xl mx-auto px-4 py-8">
        <!-- 평점 -->
        <div class="flex items-center gap-4 mb-6">
          <div class="flex items-center gap-1">
            <span class="text-amber-400 text-xl">★</span>
            <span class="text-xl font-bold text-slate-900">{{ attraction.rating.toFixed(1) }}</span>
          </div>
          <span class="text-slate-500 text-sm">리뷰 {{ attraction.reviewCount.toLocaleString() }}개</span>
          <span class="text-slate-300">|</span>
          <span class="text-slate-500 text-sm">{{ attraction.sido }} {{ attraction.gugun }}</span>
        </div>

        <!-- 설명 -->
        <div class="bg-white rounded-xl border border-slate-200 p-6 mb-4">
          <h2 class="font-semibold text-slate-900 mb-3">소개</h2>
          <p class="text-slate-700 leading-relaxed">{{ attraction.description }}</p>
        </div>

        <!-- 지도 (자리표시자) -->
        <div class="bg-white rounded-xl border border-slate-200 p-6 mb-4">
          <h2 class="font-semibold text-slate-900 mb-3">위치</h2>
          <div class="h-48 bg-slate-100 rounded-lg flex items-center justify-center">
            <div class="text-center text-slate-400">
              <p class="text-sm">지도 ({{ attraction.latitude.toFixed(4) }}, {{ attraction.longitude.toFixed(4) }})</p>
              <p class="text-xs mt-1">카카오맵 연결 예정</p>
            </div>
          </div>
          <p class="text-slate-600 text-sm mt-3">{{ attraction.address }}</p>
        </div>

        <!-- 근처 여행지 -->
        <div class="bg-white rounded-xl border border-slate-200 p-6">
          <h2 class="font-semibold text-slate-900 mb-3">같은 지역 여행지</h2>
          <div class="flex flex-col gap-3">
            <RouterLink
              v-for="nearby in nearbySameRegion"
              :key="nearby.id"
              :to="{ name: 'attraction-detail', params: { id: nearby.id } }"
              class="flex items-center gap-3 hover:bg-slate-50 rounded-lg p-2 -mx-2 transition-colors"
            >
              <img
                :src="nearby.imageUrl"
                :alt="nearby.name"
                class="w-14 h-14 object-cover rounded-lg shrink-0"
                loading="lazy"
              />
              <div>
                <p class="font-medium text-slate-900 text-sm">{{ nearby.name }}</p>
                <p class="text-slate-500 text-xs">{{ nearby.category }} · ★ {{ nearby.rating.toFixed(1) }}</p>
              </div>
            </RouterLink>
          </div>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import FavoriteButton from "@/components/FavoriteButton.vue";
import { useAttractionsStore } from "@/stores/attractions";
import { computed } from "vue";
import { RouterLink, useRoute } from "vue-router";

const route = useRoute();
const store = useAttractionsStore();

const id = Number(route.params.id);
const attraction = computed(() => store.getById(id));

const nearbySameRegion = computed(() => {
	if (!attraction.value) return [];
	return store.items
		.filter((a) => a.sido === attraction.value?.sido && a.id !== id)
		.slice(0, 4);
});
</script>
