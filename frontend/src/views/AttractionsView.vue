<script setup lang="ts">
import FavoriteButton from "@/components/FavoriteButton.vue";
import KakaoMap, { type MapMarker } from "@/components/map/KakaoMap.vue";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { useAttractionsStore } from "@/stores/attractions";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

const router = useRouter();
const store = useAttractionsStore();
const searchInput = ref(store.searchQuery);
const selectedCategory = ref(store.selectedCategory);
const selectedSido = ref(store.selectedSido);
const hoveredId = ref<number | null>(null);

/** 지도 중심: 필터된 여행지 평균 위치 or 전국 중심 */
const mapCenter = computed(() => {
	const items = store.filtered;
	if (items.length === 0) return { lat: 36.5, lng: 127.8 };
	const lat = items.reduce((s, a) => s + a.latitude, 0) / items.length;
	const lng = items.reduce((s, a) => s + a.longitude, 0) / items.length;
	return { lat, lng };
});

const mapMarkers = computed<MapMarker[]>(() =>
	store.filtered.map((a) => ({
		id: a.id,
		lat: a.latitude,
		lng: a.longitude,
		title: a.name,
	})),
);

function onSelectMarker(id: number) {
	void router.push({ name: "attraction-detail", params: { id } });
}

onMounted(() => {
	store.fetchAttractions();
});
</script>

<template>
  <div class="space-y-6">
    <!-- 헤더 -->
    <div>
      <h1 class="text-3xl font-bold tracking-tight">여행지 탐색</h1>
      <p class="text-muted-foreground mt-1">전국 {{ store.items.length }}개 여행지를 찾아보세요</p>
    </div>

    <!-- 검색 + 필터 -->
    <Card>
      <CardContent class="pt-4 pb-3">
        <div class="flex flex-col sm:flex-row gap-3">
          <Input
            v-model="searchInput"
            type="text"
            placeholder="여행지 이름 또는 주소 검색..."
            class="flex-1"
            @input="store.setSearch(searchInput)"
          />
          <Select
            v-model="selectedCategory"
            @change="store.setCategory(selectedCategory)"
          >
            <option value="">전체 카테고리</option>
            <option v-for="cat in store.categories" :key="cat" :value="cat">{{ cat }}</option>
          </Select>
          <Select
            v-model="selectedSido"
            @change="store.setSido(selectedSido)"
          >
            <option value="">전체 지역</option>
            <option v-for="sido in store.sidos" :key="sido" :value="sido">{{ sido }}</option>
          </Select>
        </div>
        <p class="text-muted-foreground text-xs mt-2">검색 결과: {{ store.filtered.length }}건</p>
      </CardContent>
    </Card>

    <!-- 지도 + 리스트 레이아웃 -->
    <div class="flex flex-col lg:flex-row gap-6">
      <!-- 카카오맵 -->
      <Card class="lg:w-1/2 overflow-hidden">
        <div class="h-64 lg:h-[640px]">
          <KakaoMap
            :center="mapCenter"
            :level="7"
            :markers="mapMarkers"
            :highlighted-marker-id="hoveredId"
            @select-marker="onSelectMarker"
          />
        </div>
      </Card>

      <!-- 리스트 -->
      <div class="lg:w-1/2 flex flex-col gap-3 overflow-y-auto max-h-screen">
        <!-- 로딩 스켈레톤 -->
        <template v-if="store.loading">
          <Card v-for="n in 4" :key="n">
            <CardContent class="p-4 animate-pulse">
              <div class="h-32 bg-muted rounded-lg mb-3" />
              <div class="h-4 bg-muted rounded w-3/4 mb-2" />
              <div class="h-3 bg-muted rounded w-1/2" />
            </CardContent>
          </Card>
        </template>

        <!-- 빈 상태 -->
        <template v-else-if="store.filtered.length === 0">
          <Card>
            <CardContent class="p-12 text-center">
              <p class="text-muted-foreground">검색 결과가 없습니다.</p>
            </CardContent>
          </Card>
        </template>

        <!-- 여행지 목록 -->
        <template v-else>
          <RouterLink
            v-for="attraction in store.filtered"
            :key="attraction.id"
            :to="{ name: 'attraction-detail', params: { id: attraction.id } }"
            class="block"
            @mouseenter="hoveredId = attraction.id"
            @mouseleave="hoveredId = null"
          >
            <Card class="overflow-hidden motion-safe:transition-[transform,box-shadow] motion-safe:duration-200 motion-safe:ease-out motion-safe:hover:-translate-y-0.5 hover:shadow-md cursor-pointer">
              <img
                :src="attraction.imageUrl"
                :alt="attraction.name"
                class="w-full h-36 object-cover"
                loading="lazy"
              />
              <CardContent class="p-4">
                <div class="flex items-start justify-between">
                  <div class="min-w-0 flex-1 mr-2">
                    <h2 class="font-semibold text-foreground truncate">{{ attraction.name }}</h2>
                    <p class="text-xs text-muted-foreground mt-0.5 truncate">{{ attraction.address }}</p>
                  </div>
                  <Badge variant="sky" class="shrink-0">{{ attraction.category }}</Badge>
                </div>
                <div class="flex items-center gap-2 mt-2">
                  <span class="text-amber-500 text-sm">★ {{ attraction.rating.toFixed(1) }}</span>
                  <span class="text-muted-foreground text-xs">({{ attraction.reviewCount.toLocaleString() }})</span>
                  <FavoriteButton :attraction-id="attraction.id" class="ml-auto" />
                </div>
              </CardContent>
            </Card>
          </RouterLink>
        </template>
      </div>
    </div>
  </div>
</template>
