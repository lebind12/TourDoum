<template>
  <main class="min-h-screen bg-slate-50">
    <div class="max-w-7xl mx-auto px-4 py-8">
      <!-- 헤더 -->
      <div class="mb-6">
        <h1 class="text-3xl font-bold text-slate-900">여행지 탐색</h1>
        <p class="text-slate-500 mt-1">전국 {{ store.items.length }}개 여행지를 찾아보세요</p>
      </div>

      <!-- 검색 + 필터 -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-6">
        <div class="flex flex-col sm:flex-row gap-3">
          <input
            v-model="searchInput"
            type="text"
            placeholder="여행지 이름 또는 주소 검색..."
            class="flex-1 px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 text-sm"
            @input="store.setSearch(searchInput)"
          />
          <select
            v-model="selectedCategory"
            class="px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 text-sm bg-white"
            @change="store.setCategory(selectedCategory)"
          >
            <option value="">전체 카테고리</option>
            <option v-for="cat in store.categories" :key="cat" :value="cat">{{ cat }}</option>
          </select>
          <select
            v-model="selectedSido"
            class="px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 text-sm bg-white"
            @change="store.setSido(selectedSido)"
          >
            <option value="">전체 지역</option>
            <option v-for="sido in store.sidos" :key="sido" :value="sido">{{ sido }}</option>
          </select>
        </div>
        <p class="text-slate-500 text-xs mt-2">검색 결과: {{ store.filtered.length }}건</p>
      </div>

      <!-- 지도 + 리스트 레이아웃 -->
      <div class="flex flex-col lg:flex-row gap-6">
        <!-- 지도 자리표시자 (BE 지도 API 연결 전) -->
        <div class="lg:w-1/2 bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
          <div class="h-64 lg:h-full min-h-64 bg-slate-100 flex items-center justify-center">
            <div class="text-center text-slate-400">
              <svg class="w-12 h-12 mx-auto mb-2 opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
              </svg>
              <p class="text-sm font-medium">지도 영역</p>
              <p class="text-xs mt-1">카카오맵 / 구글맵 연결 예정</p>
            </div>
          </div>
        </div>

        <!-- 리스트 -->
        <div class="lg:w-1/2 flex flex-col gap-4 overflow-y-auto max-h-screen">
          <template v-if="store.loading">
            <div v-for="n in 4" :key="n" class="bg-white rounded-xl border border-slate-200 p-4 animate-pulse">
              <div class="h-32 bg-slate-200 rounded-lg mb-3" />
              <div class="h-4 bg-slate-200 rounded w-3/4 mb-2" />
              <div class="h-3 bg-slate-200 rounded w-1/2" />
            </div>
          </template>

          <template v-else-if="store.filtered.length === 0">
            <div class="bg-white rounded-xl border border-slate-200 p-12 text-center">
              <p class="text-slate-400">검색 결과가 없습니다.</p>
            </div>
          </template>

          <template v-else>
            <RouterLink
              v-for="attraction in store.filtered"
              :key="attraction.id"
              :to="{ name: 'attraction-detail', params: { id: attraction.id } }"
              class="block bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm hover:shadow-md transition-shadow"
            >
              <img
                :src="attraction.imageUrl"
                :alt="attraction.name"
                class="w-full h-36 object-cover"
                loading="lazy"
              />
              <div class="p-4">
                <div class="flex items-start justify-between">
                  <div>
                    <h2 class="font-semibold text-slate-900">{{ attraction.name }}</h2>
                    <p class="text-xs text-slate-500 mt-0.5">{{ attraction.address }}</p>
                  </div>
                  <span class="text-xs bg-sky-100 text-sky-700 rounded-full px-2 py-0.5 font-medium shrink-0">
                    {{ attraction.category }}
                  </span>
                </div>
                <div class="flex items-center gap-2 mt-2">
                  <span class="text-amber-500 text-sm">★ {{ attraction.rating.toFixed(1) }}</span>
                  <span class="text-slate-400 text-xs">({{ attraction.reviewCount.toLocaleString() }})</span>
                  <FavoriteButton :attraction-id="attraction.id" class="ml-auto" />
                </div>
              </div>
            </RouterLink>
          </template>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import FavoriteButton from "@/components/FavoriteButton.vue";
import { useAttractionsStore } from "@/stores/attractions";
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const store = useAttractionsStore();
const searchInput = ref(store.searchQuery);
const selectedCategory = ref(store.selectedCategory);
const selectedSido = ref(store.selectedSido);

onMounted(() => {
	store.fetchAttractions();
});
</script>
