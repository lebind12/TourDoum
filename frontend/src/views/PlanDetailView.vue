<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs } from "@/components/ui/tabs";
import { useAccommodationsStore } from "@/stores/accommodations";
import { useAttractionsStore } from "@/stores/attractions";
import type { Plan, PlanDay, PlanItem } from "@/stores/plans";
import { usePlansStore } from "@/stores/plans";
import { GripVertical, Hotel, MapPin, X } from "lucide-vue-next";
import { computed, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const plansStore = usePlansStore();
const attractionsStore = useAttractionsStore();
const accommodationsStore = useAccommodationsStore();

const planId = String(route.params.id);
const plan = computed<Plan | undefined>(() => plansStore.getById(planId));

const activeDay = ref(0);

const currentDay = computed<PlanDay | undefined>(
	() => plan.value?.days[activeDay.value],
);

// Tabs 컴포넌트용 탭 목록 computed
const dayTabs = computed(() =>
	(plan.value?.days ?? []).map((day, idx) => ({
		key: String(idx),
		label: formatDayLabel(day, idx),
		badge: day.items.length > 0 ? day.items.length : undefined,
	})),
);

const activeDayKey = computed({
	get: () => String(activeDay.value),
	set: (v: string) => {
		activeDay.value = Number(v);
	},
});

function itemLabel(item: PlanItem): string {
	if (item.type === "attraction") {
		return (
			attractionsStore.getById(item.refId)?.name ?? `여행지 #${item.refId}`
		);
	}
	return accommodationsStore.getById(item.refId)?.name ?? `숙소 #${item.refId}`;
}

function itemCategory(item: PlanItem): string {
	if (item.type === "attraction") {
		return attractionsStore.getById(item.refId)?.category ?? "관광";
	}
	return accommodationsStore.getById(item.refId)?.type ?? "숙소";
}

function formatDayLabel(day: PlanDay, idx: number): string {
	const d = new Date(day.date);
	const mmdd = d.toLocaleDateString("ko-KR", {
		month: "short",
		day: "numeric",
	});
	return `Day ${idx + 1} (${mmdd})`;
}

function nightCount(p: Plan): number {
	return p.days.length - 1;
}

function removeItem(dayIndex: number, itemId: string) {
	plansStore.removeItem(planId, dayIndex, itemId);
}

function deletePlan() {
	if (!window.confirm("이 여행 계획을 삭제하시겠습니까?")) return;
	plansStore.deletePlan(planId);
	router.push({ name: "plans" });
}
</script>

<template>
  <!-- 404 -->
  <div v-if="!plan" class="py-20 text-center space-y-4">
    <p class="text-muted-foreground text-lg">여행 계획을 찾을 수 없습니다.</p>
    <RouterLink to="/plans">
      <Button variant="link">← 목록으로 돌아가기</Button>
    </RouterLink>
  </div>

  <div v-else class="space-y-6">
    <!-- 헤더 -->
    <div class="flex items-start justify-between gap-4">
      <div>
        <button
          type="button"
          class="text-sm text-muted-foreground hover:text-foreground mb-1 inline-flex items-center gap-1 transition-colors"
          @click="$router.back()"
        >
          ← 내 여행 계획
        </button>
        <h2 class="text-2xl font-semibold tracking-tight">{{ plan.title }}</h2>
        <div class="flex items-center gap-2 mt-1">
          <Badge variant="secondary">{{ nightCount(plan) }}박 {{ plan.days.length }}일</Badge>
          <span class="text-sm text-muted-foreground">
            {{ new Date(plan.startDate).toLocaleDateString('ko-KR') }} ~
            {{ new Date(plan.endDate).toLocaleDateString('ko-KR') }}
          </span>
        </div>
      </div>
      <Button
        variant="ghost"
        size="sm"
        class="text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
        @click="deletePlan"
      >
        삭제
      </Button>
    </div>

    <!-- 일자 탭 (Tabs 컴포넌트) -->
    <Tabs
      :tabs="dayTabs"
      v-model:active="activeDayKey"
      aria-label="여행 일자 선택"
    >
      <!-- 각 탭 패널: key = "0", "1", ... -->
      <template v-for="(day, idx) in plan.days" :key="String(idx)" #[String(idx)]>
        <!-- 아이템 없음 -->
        <div
          v-if="day.items.length === 0"
          class="rounded-xl border border-dashed py-10 text-center text-sm text-muted-foreground"
          role="status"
        >
          <span class="text-2xl block mb-2" aria-hidden="true">🗓️</span>
          이 날 일정이 없습니다.
        </div>

        <!-- 일정 타임라인 -->
        <ol v-else class="relative space-y-3 pl-8 border-l-2 border-border ml-4">
          <li
            v-for="item in day.items"
            :key="item.id"
            class="relative group"
          >
            <!-- 타임라인 점 -->
            <span
              class="absolute -left-[2.15rem] top-2 flex h-5 w-5 items-center justify-center rounded-full ring-2 ring-background"
              :class="item.type === 'attraction' ? 'bg-primary' : 'bg-amber-400'"
              aria-hidden="true"
            >
              <MapPin v-if="item.type === 'attraction'" :size="11" class="text-primary-foreground" />
              <Hotel v-else :size="11" class="text-white" />
            </span>

            <Card class="transition-shadow group-hover:shadow-sm">
              <CardContent class="p-3">
                <div class="flex items-start gap-2">
                  <!-- 드래그 핸들 시각 힌트 (실제 DnD 로직은 FE TODO) -->
                  <span
                    class="mt-0.5 shrink-0 cursor-grab text-muted-foreground/40 group-hover:text-muted-foreground/70 transition-colors"
                    aria-hidden="true"
                    title="순서 변경 (준비 중)"
                  >
                    <GripVertical :size="16" />
                  </span>

                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2 mb-0.5">
                      <span v-if="item.time" class="text-xs text-muted-foreground shrink-0">{{ item.time }}</span>
                      <Badge variant="outline" class="text-xs shrink-0">{{ itemCategory(item) }}</Badge>
                    </div>
                    <p class="font-medium text-sm truncate">{{ itemLabel(item) }}</p>
                    <p v-if="item.memo" class="text-xs text-muted-foreground mt-0.5">{{ item.memo }}</p>
                  </div>

                  <button
                    type="button"
                    class="text-muted-foreground/50 hover:text-destructive shrink-0 rounded p-0.5 transition-colors opacity-0 group-hover:opacity-100 focus-visible:opacity-100"
                    :aria-label="`${itemLabel(item)} 일정에서 제거`"
                    @click="removeItem(idx, item.id)"
                  >
                    <X :size="14" />
                  </button>
                </div>
              </CardContent>
            </Card>
          </li>
        </ol>
      </template>
    </Tabs>

    <!-- 여행지 추가 안내 (mockup) -->
    <Card class="border-dashed">
      <CardHeader class="pb-2">
        <CardTitle class="text-sm text-muted-foreground">여행지 / 숙소 추가</CardTitle>
      </CardHeader>
      <CardContent class="text-sm text-muted-foreground space-y-2">
        <p>여행지 또는 숙소 상세 페이지에서 이 계획에 추가할 수 있습니다.</p>
        <div class="flex gap-2">
          <RouterLink to="/attractions">
            <Button variant="secondary" size="sm">여행지 찾기</Button>
          </RouterLink>
          <RouterLink to="/accommodations">
            <Button variant="secondary" size="sm">숙소 찾기</Button>
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
