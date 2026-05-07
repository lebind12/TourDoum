<script setup lang="ts">
import ReservationListCard from "@/components/reservation/ReservationListCard.vue";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();

// TODO(fe): reservations store 연결 — useReservationsStore().myReservations
const myReservations = [
	/* placeholder — fe가 store에서 채움 */
];
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6">
    <div>
      <h2 class="text-2xl font-semibold tracking-tight">내 정보</h2>
      <p class="text-sm text-muted-foreground mt-1">계정 정보를 확인합니다.</p>
    </div>

    <!-- 로딩 -->
    <div v-if="authStore.loading" class="text-sm text-muted-foreground animate-pulse">
      불러오는 중...
    </div>

    <!-- 사용자 정보 카드 -->
    <Card v-else-if="authStore.currentUser">
      <CardHeader>
        <CardTitle class="text-base">계정 정보</CardTitle>
        <CardDescription>가입한 계정의 기본 정보입니다.</CardDescription>
      </CardHeader>
      <CardContent>
        <dl class="space-y-0 divide-y divide-border">
          <div class="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">ID</dt>
            <dd class="text-sm">{{ authStore.currentUser.id }}</dd>
          </div>
          <div class="flex items-center gap-4 py-3">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">이메일</dt>
            <dd class="text-sm">{{ authStore.currentUser.email }}</dd>
          </div>
          <div class="flex items-center gap-4 py-3">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">닉네임</dt>
            <dd class="text-sm font-medium text-primary">{{ authStore.currentUser.nickname }}</dd>
          </div>
          <div class="flex items-center gap-4 py-3">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">역할</dt>
            <dd class="text-sm">
              <span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold">
                {{ authStore.currentUser.role }}
              </span>
            </dd>
          </div>
          <div v-if="authStore.currentUser.createdAt" class="flex items-center gap-4 py-3 last:pb-0">
            <dt class="w-20 flex-shrink-0 text-sm font-medium text-muted-foreground">가입일</dt>
            <dd class="text-sm text-muted-foreground">{{ authStore.currentUser.createdAt }}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>

    <!-- 에러 -->
    <div
      v-else
      role="alert"
      aria-live="polite"
      class="rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"
    >
      사용자 정보를 불러올 수 없습니다.
    </div>

    <!-- ── 내 예약 섹션 ───────────────────────────────────────────── -->
    <section aria-labelledby="my-reservations-heading">
      <div class="flex items-center justify-between mb-3">
        <h2 id="my-reservations-heading" class="text-lg font-semibold tracking-tight">내 예약</h2>
        <!-- TODO(fe): 예약 목록 이동 링크 -->
      </div>

      <!-- 예약 있음 -->
      <div v-if="myReservations.length > 0" class="space-y-3">
        <ReservationListCard
          v-for="r in myReservations"
          :key="r.reservationId"
          :reservation-id="r.reservationId"
          :accommodation-name="r.accommodationName"
          :check-in="r.checkIn"
          :check-out="r.checkOut"
          :status="r.status"
          :total-price="r.totalPrice"
          @cancel="/* TODO(fe): reservations store .cancel(id) */ void 0"
        />
      </div>

      <!-- 빈 상태 -->
      <div
        v-else
        class="flex flex-col items-center gap-3 rounded-lg border border-dashed py-10 px-4 text-center"
        role="status"
        aria-label="예약 없음"
      >
        <span class="text-4xl" aria-hidden="true">🗺️</span>
        <div class="space-y-1">
          <p class="text-sm font-medium">아직 예약한 숙소가 없습니다.</p>
          <p class="text-xs text-muted-foreground">여행을 시작해 보세요!</p>
        </div>
        <button
          type="button"
          class="mt-1 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground
                 hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring transition-colors"
          aria-label="숙소 목록으로 이동하여 여행 시작하기"
          @click="/* TODO(fe): router.push('/accommodations') */ void 0"
        >
          여행 시작하기
        </button>
      </div>
    </section>
  </div>
</template>
