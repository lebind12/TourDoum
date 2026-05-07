<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { usePlansStore } from "@/stores/plans";
import { computed } from "vue";
import { RouterLink } from "vue-router";

const store = usePlansStore();
const plans = computed(() => store.sortedPlans);

function formatDateRange(start: string, end: string): string {
	const s = new Date(start).toLocaleDateString("ko-KR", {
		month: "short",
		day: "numeric",
	});
	const e = new Date(end).toLocaleDateString("ko-KR", {
		month: "short",
		day: "numeric",
	});
	return `${s} ~ ${e}`;
}

function totalAttractions(plan: (typeof plans.value)[0]): number {
	return plan.days.reduce(
		(sum, d) => sum + d.items.filter((i) => i.type === "attraction").length,
		0,
	);
}

function nightCount(plan: (typeof plans.value)[0]): number {
	return plan.days.length - 1;
}
</script>

<template>
  <div class="space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-semibold tracking-tight">내 여행 계획</h2>
        <p class="text-sm text-muted-foreground mt-1">나만의 여행 일정을 만들고 관리하세요.</p>
      </div>
      <RouterLink to="/plans/new">
        <Button>+ 새 계획 만들기</Button>
      </RouterLink>
    </div>

    <!-- 빈 상태 -->
    <div
      v-if="plans.length === 0"
      class="flex flex-col items-center gap-4 rounded-xl border border-dashed py-16 text-center"
      role="status"
    >
      <span class="text-5xl" aria-hidden="true">🗺️</span>
      <div class="space-y-1">
        <p class="text-base font-medium">아직 여행 계획이 없습니다.</p>
        <p class="text-sm text-muted-foreground">첫 번째 여행 계획을 만들어 보세요!</p>
      </div>
      <RouterLink to="/plans/new">
        <Button class="mt-2">새 계획 만들기</Button>
      </RouterLink>
    </div>

    <!-- 계획 카드 그리드 -->
    <div
      v-else
      class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
      aria-label="여행 계획 목록"
    >
      <RouterLink
        v-for="plan in plans"
        :key="plan.id"
        :to="{ name: 'plan-detail', params: { id: plan.id } }"
        class="block group"
      >
        <Card class="h-full transition-shadow hover:shadow-md group-focus-visible:ring-2 group-focus-visible:ring-ring">
          <CardHeader class="pb-2">
            <div class="flex items-start justify-between gap-2">
              <CardTitle class="text-base line-clamp-2 group-hover:text-primary transition-colors">
                {{ plan.title }}
              </CardTitle>
              <span class="text-xs text-muted-foreground shrink-0 bg-muted rounded-full px-2 py-0.5">
                {{ nightCount(plan) }}박 {{ plan.days.length }}일
              </span>
            </div>
          </CardHeader>
          <CardContent class="space-y-2">
            <div class="flex items-center gap-1.5 text-sm text-muted-foreground">
              <span aria-hidden="true">📅</span>
              {{ formatDateRange(plan.startDate, plan.endDate) }}
            </div>
            <div class="flex items-center gap-1.5 text-sm text-muted-foreground">
              <span aria-hidden="true">📍</span>
              여행지 {{ totalAttractions(plan) }}곳
            </div>
          </CardContent>
        </Card>
      </RouterLink>
    </div>
  </div>
</template>
