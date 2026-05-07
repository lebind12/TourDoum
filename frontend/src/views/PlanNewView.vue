<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { usePlansStore } from "@/stores/plans";
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
	await new Promise((r) => setTimeout(r, 150));
	const plan = store.createPlan(
		title.value.trim(),
		startDate.value,
		endDate.value,
	);
	submitting.value = false;
	router.push({ name: "plan-detail", params: { id: plan.id } });
}
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center gap-3">
      <button
        type="button"
        class="rounded-full p-1.5 hover:bg-muted transition-colors"
        aria-label="뒤로가기"
        @click="$router.back()"
      >
        <span aria-hidden="true">←</span>
      </button>
      <div>
        <h2 class="text-2xl font-semibold tracking-tight">새 여행 계획</h2>
        <p class="text-sm text-muted-foreground mt-0.5">여행 제목과 기간을 입력하세요.</p>
      </div>
    </div>

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
