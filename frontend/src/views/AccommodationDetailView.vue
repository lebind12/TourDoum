<script setup lang="ts">
import AddToPlanModal from "@/components/plan/AddToPlanModal.vue";
import ReviewForm from "@/components/review/ReviewForm.vue";
import ReviewList from "@/components/review/ReviewList.vue";
import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAccommodationsStore } from "@/stores/accommodations";
import { useAuthStore } from "@/stores/auth";
import { useReservationsStore } from "@/stores/reservations";
import { useReviewsStore } from "@/stores/reviews";
import { CalendarPlus, ChevronLeft, MessageCircle } from "lucide-vue-next";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const store = useAccommodationsStore();
const reservationsStore = useReservationsStore();
const authStore = useAuthStore();
const reviewsStore = useReviewsStore();
const id = Number(route.params.id);
const acc = computed(() => store.getById(id));

// 캐시에 없을 때 (직접 URL 진입 등) 단건 조회
onMounted(async () => {
	if (!store.getById(id)) {
		await store.fetchAccommodationById(id);
	}
});

// 현재 사용자가 이미 작성한 후기 (닉네임 기준)
const userReview = computed(() =>
	authStore.currentUser
		? reviewsStore
				.getByTarget("accommodation", id)
				.find((r) => r.authorNickname === authStore.currentUser?.nickname)
		: undefined,
);

const showReviewForm = ref(false);
const showAddToPlan = ref(false);

function handleReserve() {
	if (!acc.value) return;

	reservationsStore.start(acc.value.id);
	router.push({
		name: "reservation-dates",
		params: { accommodationId: acc.value.id },
	});
}
</script>

<template>
  <!-- 로딩 (직접 URL 진입 시) -->
  <template v-if="store.loading && !acc">
    <div class="py-20 text-center">
      <p class="text-muted-foreground">숙소 정보를 불러오는 중...</p>
    </div>
  </template>

  <!-- 에러 -->
  <template v-else-if="store.error && !acc">
    <div class="py-20 text-center space-y-4">
      <p class="text-destructive font-medium">숙소 정보를 불러오지 못했습니다.</p>
      <p class="text-muted-foreground text-sm">{{ store.error }}</p>
      <RouterLink to="/accommodations">
        <Button variant="link">← 목록으로 돌아가기</Button>
      </RouterLink>
    </div>
  </template>

  <!-- 빈 상태 (데이터 없음) -->
  <template v-else-if="!acc">
    <div class="py-20 text-center space-y-4">
      <p class="text-muted-foreground text-lg">숙소를 찾을 수 없습니다.</p>
      <RouterLink to="/accommodations">
        <Button variant="link">← 목록으로 돌아가기</Button>
      </RouterLink>
    </div>
  </template>

  <template v-else>
    <!-- 히어로 이미지 -->
    <div class="relative h-64 sm:h-80 overflow-hidden -mx-4 sm:-mx-6 lg:-mx-8 rounded-b-xl">
      <img :src="acc.imageUrl" :alt="acc.name" class="w-full h-full object-cover" />
      <div class="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent" />
      <div class="absolute bottom-0 left-0 right-0 px-6 pb-6">
        <Badge variant="emerald" class="mb-2">{{ acc.type }}</Badge>
        <h1 class="text-3xl font-bold text-white mt-1">{{ acc.name }}</h1>
        <p class="text-white/80 text-sm mt-1">{{ acc.address }}</p>
      </div>
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
    </div>

    <!-- 본문 -->
    <div class="mt-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 메인 정보 -->
      <div class="lg:col-span-2 space-y-4">
        <!-- 평점 + 기본 정보 -->
        <Card>
          <CardContent class="p-6">
            <div class="flex items-center gap-4 mb-4">
              <div class="flex items-center gap-1">
                <span class="text-amber-400 text-xl">★</span>
                <span class="text-xl font-bold">{{ acc.rating.toFixed(1) }}</span>
              </div>
              <span class="text-muted-foreground text-sm">리뷰 {{ acc.reviewCount }}개</span>
              <span class="text-border">|</span>
              <span class="text-muted-foreground text-sm">최대 {{ acc.maxGuests }}명</span>
            </div>
            <p class="text-muted-foreground leading-relaxed">{{ acc.description }}</p>
          </CardContent>
        </Card>

        <!-- 편의시설 -->
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-base">편의시설</CardTitle>
          </CardHeader>
          <CardContent>
            <div class="flex flex-wrap gap-2">
              <Badge
                v-for="amenity in acc.amenities"
                :key="amenity"
                variant="secondary"
              >
                {{ amenity }}
              </Badge>
            </div>
          </CardContent>
        </Card>

        <!-- 체크인/아웃 -->
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-base">입실/퇴실 안내</CardTitle>
          </CardHeader>
          <CardContent>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <p class="text-xs text-muted-foreground mb-1">체크인</p>
                <p class="font-semibold">{{ acc.checkInTime }}부터</p>
              </div>
              <div>
                <p class="text-xs text-muted-foreground mb-1">체크아웃</p>
                <p class="font-semibold">{{ acc.checkOutTime }}까지</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- 위치 -->
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-base">위치</CardTitle>
          </CardHeader>
          <CardContent>
            <div class="h-48 bg-muted rounded-lg flex items-center justify-center mb-3">
              <p class="text-muted-foreground text-sm">지도 연결 예정</p>
            </div>
            <p class="text-muted-foreground text-sm">{{ acc.address }}</p>
          </CardContent>
        </Card>

        <!-- 후기 섹션 -->
        <Card>
          <CardContent class="pt-6 space-y-4">
            <ReviewList target-type="accommodation" :target-id="acc.id">
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
              target-type="accommodation"
              :target-id="acc.id"
            />
          </CardContent>
        </Card>
      </div>

      <!-- 사이드 패널 -->
      <div class="space-y-4">
        <!-- 예약 카드 -->
        <Card class="sticky top-4 shadow-sm">
          <CardContent class="p-6">
            <p class="text-2xl font-bold text-primary">
              {{ acc.pricePerNight.toLocaleString() }}원
              <span class="text-muted-foreground font-normal text-sm">/ 1박</span>
            </p>
            <div class="mt-4 space-y-3">
              <div class="space-y-1">
                <Label>체크인</Label>
                <Input type="date" />
              </div>
              <div class="space-y-1">
                <Label>체크아웃</Label>
                <Input type="date" />
              </div>
            </div>
            <Button class="w-full mt-4" type="button" @click="handleReserve">
              예약하기
            </Button>
            <Button
              v-if="authStore.currentUser"
              variant="outline"
              class="w-full mt-2 gap-2"
              type="button"
              @click="showAddToPlan = true"
            >
              <CalendarPlus class="w-4 h-4" />
              계획에 추가
            </Button>
            <p class="text-center text-muted-foreground text-xs mt-2">
              다음 단계에서 날짜와 인원을 확정합니다.
            </p>
          </CardContent>
        </Card>

        <!-- 호스트 -->
        <Card>
          <CardHeader class="pb-2">
            <CardTitle class="text-base">호스트</CardTitle>
          </CardHeader>
          <CardContent>
            <div class="flex items-center gap-3 mb-4">
              <Avatar :name="acc.hostName" variant="sky" size="md" />
              <div>
                <p class="font-medium text-sm">{{ acc.hostName }}</p>
                <p class="text-muted-foreground text-xs">호스트</p>
              </div>
            </div>
            <RouterLink :to="{ name: 'dm', params: { userId: acc.hostId } }">
              <Button variant="secondary" class="w-full gap-2">
                <MessageCircle class="w-4 h-4" />
                1:1 메시지 보내기
              </Button>
            </RouterLink>
          </CardContent>
        </Card>
      </div>
    </div>
  </template>

  <!-- 계획에 추가 모달 -->
  <Teleport to="body">
    <AddToPlanModal
      v-if="showAddToPlan && acc"
      target-type="accommodation"
      :target-id="acc.id"
      :target-name="acc.name"
      @close="showAddToPlan = false"
    />
  </Teleport>
</template>
