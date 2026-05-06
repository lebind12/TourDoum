<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { useHealthStore } from "@/stores/health";
import { onMounted } from "vue";
import { useRouter } from "vue-router";

const authStore = useAuthStore();
const healthStore = useHealthStore();
const router = useRouter();

onMounted(() => {
	healthStore.fetchHealth();
});

async function handleLogout() {
	await authStore.logout();
	router.push("/");
}
</script>

<template>
  <div class="space-y-6">
    <!-- 환영 / 게스트 배너 -->
    <div
      v-if="authStore.currentUser"
      class="flex items-center gap-4 rounded-lg border bg-primary/5 px-4 py-3"
    >
      <p class="flex-1 text-sm">
        안녕하세요, <strong class="font-semibold text-primary">{{ authStore.currentUser.nickname }}</strong>님
      </p>
      <div class="flex gap-2">
        <RouterLink
          to="/me"
          class="inline-flex h-8 items-center justify-center rounded-md border border-input bg-background px-3 text-xs font-medium ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          내 정보
        </RouterLink>
        <button
          type="button"
          :disabled="authStore.loading"
          class="inline-flex h-8 items-center justify-center rounded-md px-3 text-xs font-medium text-muted-foreground ring-offset-background transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50"
          @click="handleLogout"
        >
          {{ authStore.loading ? '처리 중...' : '로그아웃' }}
        </button>
      </div>
    </div>

    <div
      v-else
      class="flex items-center gap-3 rounded-lg border bg-muted/50 px-4 py-3"
    >
      <p class="flex-1 text-sm text-muted-foreground">로그인하여 TourDoum을 이용하세요.</p>
      <RouterLink
        to="/login"
        class="inline-flex h-8 items-center justify-center rounded-md border border-input bg-background px-3 text-xs font-medium ring-offset-background transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        로그인
      </RouterLink>
      <RouterLink
        to="/signup"
        class="inline-flex h-8 items-center justify-center rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground ring-offset-background transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        회원가입
      </RouterLink>
    </div>

    <!-- 서버 상태 카드 -->
    <div class="rounded-lg border bg-card text-card-foreground shadow-sm">
      <div class="flex flex-col space-y-1.5 p-6">
        <h2 class="text-xl font-semibold leading-none tracking-tight">서버 상태</h2>
        <p class="text-sm text-muted-foreground">
          백엔드가 실행 중이면 <code class="rounded bg-muted px-1 py-0.5 text-xs">GET /api/health</code> 응답이 표시됩니다.
        </p>
      </div>

      <div class="p-6 pt-0">
        <div v-if="healthStore.loading" class="inline-flex items-center gap-2 rounded-md bg-muted px-3 py-1.5 text-sm text-muted-foreground">
          <span class="h-2 w-2 animate-pulse rounded-full bg-muted-foreground" />
          확인 중...
        </div>

        <div v-else-if="healthStore.status" class="inline-flex items-center gap-2 rounded-md bg-green-50 px-3 py-1.5 text-sm font-medium text-green-700">
          <span class="h-2 w-2 rounded-full bg-green-500" />
          {{ healthStore.status }}
        </div>

        <div v-else class="inline-flex items-center gap-2 rounded-md bg-destructive/10 px-3 py-1.5 text-sm font-medium text-destructive">
          <span class="h-2 w-2 rounded-full bg-destructive" />
          백엔드 미가동
        </div>

        <p class="mt-4 text-sm text-muted-foreground">
          여행지 검색 / 숙박 추천 기능은 이후 개발 예정입니다.
        </p>
      </div>
    </div>
  </div>
</template>
