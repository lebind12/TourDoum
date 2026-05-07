<script setup lang="ts">
/**
 * AddToPlanModal — 여행 계획에 여행지/숙박을 추가하는 모달.
 *
 * 1단계: 계획 선택
 * 2단계: 일차(Day) 선택
 * 완료: plansStore.addItem 호출 후 닫힘
 */
import { Button } from "@/components/ui/button";
import type { PlanItemType } from "@/stores/plans";
import { usePlansStore } from "@/stores/plans";
import { X } from "lucide-vue-next";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";

const props = defineProps<{
	targetType: PlanItemType;
	targetId: number;
	targetName: string;
}>();

const emit = defineEmits<{
	close: [];
	added: [planId: string, dayIndex: number];
}>();

const router = useRouter();
const plansStore = usePlansStore();

const step = ref<"plan" | "day">("plan");
const selectedPlanId = ref<string | null>(null);
const submitting = ref(false);
const errorMsg = ref<string | null>(null);

const selectedPlan = computed(() =>
	selectedPlanId.value ? plansStore.getById(selectedPlanId.value) : null,
);

onMounted(async () => {
	if (plansStore.plans.length === 0) {
		await plansStore.fetchMyPlans();
	}
});

function selectPlan(id: string) {
	selectedPlanId.value = id;
	step.value = "day";
}

async function selectDay(dayIndex: number) {
	if (!selectedPlanId.value) return;
	submitting.value = true;
	errorMsg.value = null;

	const result = await plansStore.addItem(selectedPlanId.value, dayIndex, {
		type: props.targetType,
		refId: props.targetId,
	});
	submitting.value = false;

	if (!result) {
		errorMsg.value = plansStore.error ?? "추가에 실패했습니다.";
		return;
	}

	emit("added", selectedPlanId.value, dayIndex);
	emit("close");
}

function goToPlanNew() {
	emit("close");
	router.push({ name: "plan-new" });
}
</script>

<template>
  <!-- 배경 오버레이 -->
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    role="dialog"
    aria-modal="true"
    aria-label="계획에 추가"
    @click.self="emit('close')"
  >
    <div class="bg-background rounded-xl shadow-xl w-full max-w-sm mx-4 max-h-[80vh] flex flex-col">
      <!-- 헤더 -->
      <div class="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
        <h2 class="font-semibold text-sm">
          {{ step === 'plan' ? '계획 선택' : `${selectedPlan?.title ?? ''} — 일차 선택` }}
        </h2>
        <button
          type="button"
          class="text-muted-foreground hover:text-foreground"
          aria-label="닫기"
          @click="emit('close')"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- 추가 대상 정보 -->
      <p class="px-4 pt-3 text-xs text-muted-foreground shrink-0">
        <span class="font-medium text-foreground">{{ targetName }}</span>을(를) 계획에 추가합니다.
      </p>

      <!-- 오류 메시지 -->
      <p v-if="errorMsg" class="px-4 pt-2 text-xs text-destructive shrink-0" role="alert">
        {{ errorMsg }}
      </p>

      <!-- 1단계: 계획 목록 -->
      <template v-if="step === 'plan'">
        <div v-if="plansStore.loading" class="flex-1 flex items-center justify-center p-8">
          <p class="text-muted-foreground text-sm">계획 목록을 불러오는 중...</p>
        </div>
        <div v-else-if="plansStore.sortedPlans.length === 0" class="flex-1 flex flex-col items-center justify-center p-8 gap-3">
          <p class="text-muted-foreground text-sm">아직 여행 계획이 없습니다.</p>
          <Button size="sm" @click="goToPlanNew">새 계획 만들기</Button>
        </div>
        <ul v-else class="flex-1 overflow-y-auto divide-y divide-border">
          <li
            v-for="plan in plansStore.sortedPlans"
            :key="plan.id"
          >
            <button
              type="button"
              class="w-full text-left px-4 py-3 hover:bg-muted/50 transition-colors"
              @click="selectPlan(plan.id)"
            >
              <p class="text-sm font-medium">{{ plan.title }}</p>
              <p class="text-xs text-muted-foreground mt-0.5">
                {{ plan.startDate }} ~ {{ plan.endDate }} ({{ plan.days.length }}일)
              </p>
            </button>
          </li>
        </ul>
      </template>

      <!-- 2단계: 일차 목록 -->
      <template v-else-if="step === 'day' && selectedPlan">
        <ul class="flex-1 overflow-y-auto divide-y divide-border">
          <li
            v-for="(day, idx) in selectedPlan.days"
            :key="idx"
          >
            <button
              type="button"
              class="w-full text-left px-4 py-3 hover:bg-muted/50 transition-colors disabled:opacity-50"
              :disabled="submitting"
              @click="selectDay(idx)"
            >
              <p class="text-sm font-medium">Day {{ idx + 1 }}</p>
              <p class="text-xs text-muted-foreground mt-0.5">
                {{ day.date }} · {{ day.items.length }}개 항목
              </p>
            </button>
          </li>
        </ul>

        <div class="px-4 py-3 border-t border-border shrink-0">
          <Button variant="ghost" size="sm" class="w-full" @click="step = 'plan'">
            ← 계획 다시 선택
          </Button>
        </div>
      </template>
    </div>
  </div>
</template>
