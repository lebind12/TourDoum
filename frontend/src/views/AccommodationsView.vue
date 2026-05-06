<template>
  <main class="min-h-screen bg-slate-50">
    <div class="max-w-6xl mx-auto px-4 py-8">
      <!-- 헤더 -->
      <div class="mb-6">
        <h1 class="text-3xl font-bold text-slate-900">숙박 검색</h1>
        <p class="text-slate-500 mt-1">{{ store.items.length }}개 숙박 시설이 준비되어 있습니다</p>
      </div>

      <!-- 필터 -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-6">
        <div class="flex flex-col sm:flex-row gap-3">
          <input
            v-model="searchInput"
            type="text"
            placeholder="숙소 이름 또는 지역 검색..."
            class="flex-1 px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 text-sm"
            @input="store.setSearch(searchInput)"
          />
          <select
            v-model="selectedType"
            class="px-4 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 text-sm bg-white"
            @change="store.setType(selectedType)"
          >
            <option value="">전체 숙소 유형</option>
            <option v-for="t in store.types" :key="t" :value="t">{{ t }}</option>
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

      <!-- 목록 -->
      <template v-if="store.loading">
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <div v-for="n in 6" :key="n" class="bg-white rounded-xl border border-slate-200 animate-pulse">
            <div class="h-48 bg-slate-200 rounded-t-xl" />
            <div class="p-4">
              <div class="h-4 bg-slate-200 rounded w-3/4 mb-2" />
              <div class="h-3 bg-slate-200 rounded w-1/2" />
            </div>
          </div>
        </div>
      </template>

      <template v-else-if="store.filtered.length === 0">
        <div class="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <p class="text-slate-400">검색 결과가 없습니다.</p>
        </div>
      </template>

      <template v-else>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <RouterLink
            v-for="acc in store.filtered"
            :key="acc.id"
            :to="{ name: 'accommodation-detail', params: { id: acc.id } }"
            class="block bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm hover:shadow-md transition-shadow"
          >
            <img
              :src="acc.imageUrl"
              :alt="acc.name"
              class="w-full h-48 object-cover"
              loading="lazy"
            />
            <div class="p-4">
              <div class="flex items-start justify-between mb-1">
                <h2 class="font-semibold text-slate-900 text-sm">{{ acc.name }}</h2>
                <span class="text-xs bg-emerald-100 text-emerald-700 rounded-full px-2 py-0.5 font-medium shrink-0">
                  {{ acc.type }}
                </span>
              </div>
              <p class="text-xs text-slate-500">{{ acc.sido }} {{ acc.gugun }}</p>
              <div class="flex items-center gap-2 mt-2">
                <span class="text-amber-500 text-sm">★ {{ acc.rating.toFixed(1) }}</span>
                <span class="text-slate-400 text-xs">({{ acc.reviewCount }})</span>
              </div>
              <p class="text-sky-600 font-semibold text-sm mt-2">
                {{ acc.pricePerNight.toLocaleString() }}원 <span class="text-slate-400 font-normal text-xs">/ 1박</span>
              </p>
            </div>
          </RouterLink>
        </div>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { useAccommodationsStore } from "@/stores/accommodations";
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const store = useAccommodationsStore();
const searchInput = ref(store.searchQuery);
const selectedType = ref(store.selectedType);
const selectedSido = ref(store.selectedSido);

onMounted(() => {
	store.fetchAccommodations();
});
</script>
