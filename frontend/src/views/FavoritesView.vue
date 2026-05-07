<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useAttractionsStore } from "@/stores/attractions";
import { useFavoritesStore } from "@/stores/favorites";
import { Heart, X } from "lucide-vue-next";
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

<template>
  <div class="space-y-6">
    <!-- 헤더 -->
    <div>
      <h1 class="text-3xl font-bold tracking-tight">즐겨찾기</h1>
      <p class="text-muted-foreground mt-1">
        내가 저장한 여행지 {{ favoritesStore.favoriteAttractions.length }}곳
      </p>
    </div>

    <!-- 에러 -->
    <template v-if="favoritesStore.error">
      <Card>
        <CardContent class="p-12 text-center space-y-3">
          <p class="text-destructive font-medium">즐겨찾기를 불러오지 못했습니다.</p>
          <p class="text-muted-foreground text-sm">{{ favoritesStore.error }}</p>
          <button
            type="button"
            class="text-sm text-primary underline underline-offset-2"
            @click="favoritesStore.fetchFavorites()"
          >
            다시 시도
          </button>
        </CardContent>
      </Card>
    </template>

    <!-- 빈 상태 -->
    <template v-else-if="favoritesStore.favoriteAttractions.length === 0">
      <Card>
        <CardContent class="p-16 text-center">
          <Heart class="w-16 h-16 mx-auto text-muted-foreground/30 mb-4" aria-hidden="true" />
          <p class="text-foreground text-lg font-medium">아직 즐겨찾기한 여행지가 없어요</p>
          <p class="text-muted-foreground text-sm mt-2">여행지를 탐색하고 마음에 드는 곳을 저장해보세요</p>
          <RouterLink to="/attractions" class="mt-6 inline-block">
            <Button>여행지 탐색하기</Button>
          </RouterLink>
        </CardContent>
      </Card>
    </template>

    <!-- 즐겨찾기 목록 -->
    <template v-else-if="favoritesStore.favoriteAttractions.length > 0">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Card
          v-for="attraction in favoritesStore.favoriteAttractions"
          :key="attraction.id"
          class="overflow-hidden"
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
            <!-- 즐겨찾기 제거 버튼 -->
            <Button
              type="button"
              variant="ghost"
              size="icon"
              class="absolute top-2 right-2 bg-background/90 backdrop-blur-sm hover:bg-destructive/10 hover:text-destructive rounded-full shadow"
              :aria-label="`${attraction.name} 즐겨찾기 제거`"
              @click="handleRemove(attraction.id)"
            >
              <X class="w-4 h-4" />
            </Button>
          </div>
          <RouterLink
            :to="{ name: 'attraction-detail', params: { id: attraction.id } }"
            class="block"
          >
            <CardContent class="p-4">
              <div class="flex items-start justify-between gap-2">
                <h2 class="font-semibold truncate">{{ attraction.name }}</h2>
                <Badge variant="sky" class="shrink-0">{{ attraction.category }}</Badge>
              </div>
              <p class="text-xs text-muted-foreground mt-1 truncate">{{ attraction.address }}</p>
              <div class="flex items-center gap-2 mt-2">
                <span class="text-amber-500 text-sm">★ {{ attraction.rating.toFixed(1) }}</span>
                <span class="text-muted-foreground text-xs">({{ attraction.reviewCount.toLocaleString() }})</span>
              </div>
            </CardContent>
          </RouterLink>
        </Card>
      </div>
    </template>
  </div>
</template>
