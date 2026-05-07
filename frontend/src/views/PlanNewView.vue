<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { usePlansStore } from "@/stores/plans";
import { Check, ChevronLeft } from "lucide-vue-next";
import { computed, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const store = usePlansStore();

const title = ref("");
const startDate = ref("");
const endDate = ref("");
const errorMsg = ref<string | null>(null);
const submitting = ref(false);

const todayStr = new Date().toISOString().slice(0, 10);

// ── 단계 인디케이터 ──────────────────────────────────────────────────────
const steps = [
	{ label: "제목", done: computed(() => title.value.trim().length > 0) },
	{
		label: "기간",
		done: computed(
			() =>
				!!startDate.value &&
				!!endDate.value &&
				endDate.value >= startDate.value,
		),
	},
	{ label: "생성", done: computed(() => false) },
];

const currentStep = computed(() => {
	if (!title.value.trim()) return 0;
	if (!startDate.value || !endDate.value || endDate.value < startDate.value)
		return 1;
	return 2;
});

const nightCount = computed(() => {
	if (!startDate.value || !endDate.value) return 0;
	const diff =
		new Date(endDate.value).getTime() - new Date(startDate.value).getTime();
	return Math.max(0, Math.floor(diff / 86400000));
});

function validate(): string | null {
	if (!title.value.trim()) return "여행 제목을 입력해 주세요.";
	if (!startDate.value) return "출발일을 선택해 주세요.";
	if (!endDate.value) return "귀가일을 선택해 주세요.";
	if (endDate.value < startDate.value)
		return "귀가일은 출발일 이후여야 합니다.";
	const nights = nightCount.value;
	if (nights > 30) return "여행 기간은 최대 30박까지 지원합니다.";
	return null;
}

async function submit() {
	const err = validate();
	if (err) {
		errorMsg.value = err;
		return;
	}
	errorMsg.value = null;
	submitting.value = true;
	const plan = await store.createPlan(
		title.value.trim(),
		startDate.value,
		endDate.value,
	);
	submitting.value = false;
	if (!plan) {
		errorMsg.value = store.error ?? "계획 생성에 실패했습니다.";
		return;
	}
	router.push({ name: "plan-detail", params: { id: plan.id } });
}
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center gap-3">
      <button
        type="button"
        class="rounded-full p-1.5 hover:bg-muted transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label="뒤로가기"
        @click="$router.back()"
      >
        <ChevronLeft :size="18" aria-hidden="true" />
      </button>
      <div>
        <h2 class="text-2xl font-semibold tracking-tight">새 여행 계획</h2>
        <p class="text-sm text-muted-foreground mt-0.5">여행 제목과 기간을 입력하세요.</p>
      </div>
    </div>

    <!-- 단계 인디케이터 -->
    <nav aria-label="폼 단계" class="flex items-center gap-0">
      <template v-for="(step, idx) in steps" :key="step.label">
        <!-- 단계 원 -->
        <div class="flex flex-col items-center">
          <div
            class="flex h-8 w-8 items-center justify-center rounded-full border-2 text-xs font-semibold transition-colors"
            :class="[
              step.done.value
                ? 'border-primary bg-primary text-primary-foreground'
                : currentStep === idx
                  ? 'border-primary bg-background text-primary'
                  : 'border-muted-foreground/30 bg-background text-muted-foreground/50'
            ]"
            :aria-current="currentStep === idx ? 'step' : undefined"
          >
            <Check v-if="step.done.value" :size="14" aria-hidden="true" />
            <span v-else aria-hidden="true">{{ idx + 1 }}</span>
          </div>
          <span
            class="mt-1 text-xs transition-colors"
            :class="currentStep >= idx ? 'text-foreground font-medium' : 'text-muted-foreground/50'"
          >{{ step.label }}</span>
        </div>

        <!-- 구분선 (마지막 제외) -->
        <div
          v-if="idx < steps.length - 1"
          class="mb-4 h-0.5 flex-1 mx-1 transition-colors"
          :class="step.done.value ? 'bg-primary' : 'bg-muted-foreground/20'"
          aria-hidden="true"
        />
      </template>
    </nav>

    <!-- 폼 -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">기본 정보</CardTitle>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit.prevent="submit">
          <!-- 제목 -->
          <div class="space-y-1.5">
            <Label for="plan-title">여행 제목 *</Label>
            <Input
              id="plan-title"
              v-model="title"
              placeholder="예: 제주도 3박 4일 힐링 여행"
              maxlength="60"
              aria-required="true"
            />
          </div>

          <!-- 날짜 -->
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5">
              <Label for="plan-start">출발일 *</Label>
              <Input
                id="plan-start"
                v-model="startDate"
                type="date"
                :min="todayStr"
                aria-required="true"
              />
            </div>
            <div class="space-y-1.5">
              <Label for="plan-end">귀가일 *</Label>
              <Input
                id="plan-end"
                v-model="endDate"
                type="date"
                :min="startDate || todayStr"
                aria-required="true"
              />
            </div>
          </div>

          <!-- 박수 미리보기 -->
          <p v-if="nightCount > 0" class="text-sm text-muted-foreground">
            → {{ nightCount }}박 {{ nightCount + 1 }}일 일정
          </p>

          <!-- 에러 -->
          <p
            v-if="errorMsg"
            role="alert"
            aria-live="assertive"
            class="text-sm text-destructive"
          >{{ errorMsg }}</p>

          <!-- 제출 -->
          <Button type="submit" class="w-full" :disabled="submitting">
            {{ submitting ? '생성 중...' : '계획 만들기' }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
