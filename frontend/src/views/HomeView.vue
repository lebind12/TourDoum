<script setup lang="ts">
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
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
    <Card v-if="authStore.currentUser" class="border-primary/20 bg-primary/5">
      <CardContent class="flex items-center gap-4 py-3 px-4">
        <p class="flex-1 text-sm">
          안녕하세요, <strong class="font-semibold text-primary">{{ authStore.currentUser.nickname }}</strong>님
        </p>
        <div class="flex gap-2">
          <RouterLink to="/me">
            <Button variant="outline" size="sm">내 정보</Button>
          </RouterLink>
          <Button
            variant="ghost"
            size="sm"
            :disabled="authStore.loading"
            @click="handleLogout"
          >
            {{ authStore.loading ? '처리 중...' : '로그아웃' }}
          </Button>
        </div>
      </CardContent>
    </Card>

    <Card v-else class="bg-muted/50">
      <CardContent class="flex items-center gap-3 py-3 px-4">
        <p class="flex-1 text-sm text-muted-foreground">로그인하여 TourDoum을 이용하세요.</p>
        <RouterLink to="/login">
          <Button variant="outline" size="sm">로그인</Button>
        </RouterLink>
        <RouterLink to="/signup">
          <Button size="sm">회원가입</Button>
        </RouterLink>
      </CardContent>
    </Card>

    <!-- 서버 상태 카드 -->
    <Card>
      <CardHeader>
        <CardTitle class="text-xl">서버 상태</CardTitle>
        <CardDescription>
          백엔드가 실행 중이면 <code class="rounded bg-muted px-1 py-0.5 text-xs">GET /api/health</code> 응답이 표시됩니다.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <div v-if="healthStore.loading" class="inline-flex items-center gap-2 rounded-md bg-muted px-3 py-1.5 text-sm text-muted-foreground">
          <span class="h-2 w-2 animate-pulse rounded-full bg-muted-foreground" />
          확인 중...
        </div>

        <div v-else-if="healthStore.status" class="inline-flex items-center gap-2 rounded-md bg-success/10 px-3 py-1.5 text-sm font-medium text-success">
          <span class="h-2 w-2 rounded-full bg-success" />
          {{ healthStore.status }}
        </div>

        <div v-else class="inline-flex items-center gap-2 rounded-md bg-destructive/10 px-3 py-1.5 text-sm font-medium text-destructive">
          <span class="h-2 w-2 rounded-full bg-destructive" />
          백엔드 미가동
        </div>

        <p class="mt-4 text-sm text-muted-foreground">
          여행지 검색 / 숙박 추천 기능은 이후 개발 예정입니다.
        </p>
      </CardContent>
    </Card>
  </div>
</template>
