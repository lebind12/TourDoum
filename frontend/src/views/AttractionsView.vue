<script setup lang="ts">
import FavoriteButton from "@/components/FavoriteButton.vue";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useAttractionsStore } from "@/stores/attractions";
import { MapPin } from "lucide-vue-next";
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
          <select
            v-model="selectedCategory"
            class="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            @change="store.setCategory(selectedCategory)"
          >
            <option value="">전체 카테고리</option>
            <option v-for="cat in store.categories" :key="cat" :value="cat">{{ cat }}</option>
          </select>
          <select
            v-model="selectedSido"
            class="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            @change="store.setSido(selectedSido)"
          >
            <option value="">전체 지역</option>
            <option v-for="sido in store.sidos" :key="sido" :value="sido">{{ sido }}</option>
          </select>
        </div>
        <p class="text-muted-foreground text-xs mt-2">검색 결과: {{ store.filtered.length }}건</p>
      </CardContent>
    </Card>

    <!-- 지도 + 리스트 레이아웃 -->
    <div class="flex flex-col lg:flex-row gap-6">
      <!-- 지도 자리표시자 -->
      <Card class="lg:w-1/2 overflow-hidden">
        <div class="h-64 lg:h-full min-h-64 bg-muted flex items-center justify-center">
          <div class="text-center text-muted-foreground">
            <MapPin class="w-12 h-12 mx-auto mb-2 opacity-40" />
            <p class="text-sm font-medium">지도 영역</p>
            <p class="text-xs mt-1">카카오맵 / 구글맵 연결 예정</p>
          </div>
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
          >
            <Card class="overflow-hidden hover:shadow-md transition-shadow cursor-pointer">
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
