<script setup lang="ts">
import FavoriteButton from "@/components/FavoriteButton.vue";
import KakaoMap, { type MapMarker } from "@/components/map/KakaoMap.vue";
import ReviewForm from "@/components/review/ReviewForm.vue";
import ReviewList from "@/components/review/ReviewList.vue";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAttractionsStore } from "@/stores/attractions";
import { useAuthStore } from "@/stores/auth";
import { useReviewsStore } from "@/stores/reviews";
import { ChevronLeft } from "lucide-vue-next";
import { computed, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const store = useAttractionsStore();
const authStore = useAuthStore();
const reviewsStore = useReviewsStore();

const id = Number(route.params.id);
const attraction = computed(() => store.getById(id));

// 현재 사용자가 이미 작성한 후기 (닉네임 기준)
const userReview = computed(() =>
	authStore.currentUser
		? reviewsStore
				.getByTarget("attraction", id)
				.find((r) => r.authorNickname === authStore.currentUser?.nickname)
		: undefined,
);

const showReviewForm = ref(false);

const nearbySameRegion = computed(() => {
	if (!attraction.value) return [];
	return store.items
		.filter((a) => a.sido === attraction.value?.sido && a.id !== id)
		.slice(0, 4);
});

const detailCenter = computed(() => {
	if (!attraction.value) return { lat: 36.5, lng: 127.8 };
	return { lat: attraction.value.latitude, lng: attraction.value.longitude };
});

const detailMarkers = computed<MapMarker[]>(() => {
	if (!attraction.value) return [];
	return [
		{
			id: attraction.value.id,
			lat: attraction.value.latitude,
			lng: attraction.value.longitude,
			title: attraction.value.name,
		},
	];
});
</script>

<template>
  <!-- 빈 상태 -->
  <template v-if="!attraction">
    <div class="py-20 text-center space-y-4">
      <p class="text-muted-foreground text-lg">여행지를 찾을 수 없습니다.</p>
      <RouterLink to="/attractions">
        <Button variant="link">← 목록으로 돌아가기</Button>
      </RouterLink>
    </div>
  </template>

  <template v-else>
    <!-- 히어로 이미지 -->
    <div class="relative h-64 sm:h-80 overflow-hidden -mx-4 sm:-mx-6 lg:-mx-8 rounded-b-xl">
      <img
        :src="attraction.imageUrl"
        :alt="attraction.name"
        class="w-full h-full object-cover"
      />
      <div class="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent" />
      <div class="absolute bottom-0 left-0 right-0 px-6 pb-6">
        <Badge class="bg-primary text-primary-foreground mb-2">{{ attraction.category }}</Badge>
        <h1 class="text-3xl font-bold text-white mt-1">{{ attraction.name }}</h1>
        <p class="text-white/80 text-sm mt-1">{{ attraction.address }}</p>
      </div>
      <!-- 뒤로가기 -->
      <Button
        type="button"
        variant="ghost"
        size="icon"
        class="absolute top-4 left-4 bg-white/20 backdrop-blur-sm text-white hover:bg-white/30 hover:text-white rounded-full"
        aria-label="뒤로가기"
        @click="$router.back()"
      >
        <ChevronLeft class="w-5 h-5" />
      </Button>
      <!-- 즐겨찾기 -->
      <div class="absolute top-4 right-4">
        <FavoriteButton :attraction-id="attraction.id" size="lg" />
      </div>
    </div>

    <!-- 상세 정보 -->
    <div class="max-w-3xl mx-auto mt-6 space-y-4">
      <!-- 평점 -->
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-1">
          <span class="text-amber-400 text-xl">★</span>
          <span class="text-xl font-bold">{{ attraction.rating.toFixed(1) }}</span>
        </div>
        <span class="text-muted-foreground text-sm">리뷰 {{ attraction.reviewCount.toLocaleString() }}개</span>
        <span class="text-border">|</span>
        <span class="text-muted-foreground text-sm">{{ attraction.sido }} {{ attraction.gugun }}</span>
      </div>

      <!-- 소개 -->
      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-base">소개</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-muted-foreground leading-relaxed">{{ attraction.description }}</p>
        </CardContent>
      </Card>

      <!-- 위치 -->
      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-base">위치</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="h-48 rounded-lg overflow-hidden mb-3">
            <KakaoMap
              :center="detailCenter"
              :level="4"
              :markers="detailMarkers"
            />
          </div>
          <p class="text-sm text-muted-foreground">{{ attraction.address }}</p>
        </CardContent>
      </Card>

      <!-- 같은 지역 여행지 -->
      <Card>
        <CardHeader class="pb-2">
          <CardTitle class="text-base">같은 지역 여행지</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex flex-col divide-y divide-border">
            <RouterLink
              v-for="nearby in nearbySameRegion"
              :key="nearby.id"
              :to="{ name: 'attraction-detail', params: { id: nearby.id } }"
              class="flex items-center gap-3 hover:bg-muted/50 rounded-lg p-2 -mx-2 transition-colors"
            >
              <img
                :src="nearby.imageUrl"
                :alt="nearby.name"
                class="w-14 h-14 object-cover rounded-lg shrink-0"
                loading="lazy"
              />
              <div>
                <p class="font-medium text-sm">{{ nearby.name }}</p>
                <p class="text-muted-foreground text-xs">{{ nearby.category }} · ★ {{ nearby.rating.toFixed(1) }}</p>
              </div>
            </RouterLink>
          </div>
        </CardContent>
      </Card>

      <!-- 후기 섹션 -->
      <Card>
        <CardContent class="pt-6 space-y-4">
          <ReviewList target-type="attraction" :target-id="attraction.id">
            <template #cta>
              <!-- 비로그인 -->
              <Button
                v-if="!authStore.currentUser"
                variant="outline"
                size="sm"
                @click="router.push('/login')"
              >
                로그인하고 후기 작성하기
              </Button>
              <!-- 이미 후기 있음 -->
              <Button
                v-else-if="userReview"
                variant="ghost"
                size="sm"
                @click="showReviewForm = !showReviewForm"
              >
                내 후기 보기/수정
              </Button>
              <!-- 후기 작성 가능 -->
              <Button
                v-else
                size="sm"
                @click="showReviewForm = !showReviewForm"
              >
                후기 작성
              </Button>
            </template>
          </ReviewList>

          <!-- 후기 폼 (토글) -->
          <ReviewForm
            v-if="showReviewForm"
            target-type="attraction"
            :target-id="attraction.id"
          />
        </CardContent>
      </Card>
    </div>
  </template>
</template>
