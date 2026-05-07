<script setup lang="ts">
/**
 * ReservationStepper
 *
 * Props:
 *   currentStep: 1 | 2 | 3  — 현재 활성 단계
 *
 * 사용 예:
 *   <ReservationStepper :current-step="1" />
 */
interface Props {
	currentStep: 1 | 2 | 3;
}
const props = defineProps<Props>();

const steps = [
	{ id: 1, label: "날짜·인원" },
	{ id: 2, label: "결제 수단" },
	{ id: 3, label: "예약 완료" },
] as const;
</script>

<template>
  <nav aria-label="예약 단계" class="w-full">
    <ol class="flex items-center justify-between">
      <template v-for="(step, idx) in steps" :key="step.id">
        <!-- 단계 항목 -->
        <li class="flex flex-col items-center gap-1.5 flex-1">
          <span
            :aria-current="step.id === props.currentStep ? 'step' : undefined"
            class="inline-flex h-9 w-9 items-center justify-center rounded-full border-2 text-sm font-semibold transition-colors"
            :class="{
              'border-primary bg-primary text-primary-foreground': step.id === props.currentStep,
              'border-primary bg-background text-primary': step.id < props.currentStep,
              'border-border bg-background text-muted-foreground': step.id > props.currentStep,
            }"
          >
            <!-- 완료 단계: 체크 아이콘 -->
            <template v-if="step.id < props.currentStep">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-4 w-4"
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path
                  fill-rule="evenodd"
                  d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                  clip-rule="evenodd"
                />
              </svg>
            </template>
            <template v-else>{{ step.id }}</template>
          </span>
          <span
            class="text-xs font-medium hidden sm:block"
            :class="{
              'text-primary': step.id <= props.currentStep,
              'text-muted-foreground': step.id > props.currentStep,
            }"
          >{{ step.label }}</span>
        </li>

        <!-- 연결선 (마지막 제외) -->
        <li
          v-if="idx < steps.length - 1"
          aria-hidden="true"
          class="flex-1 h-px mx-2 transition-colors"
          :class="{
            'bg-primary': idx + 1 < props.currentStep,
            'bg-border': idx + 1 >= props.currentStep,
          }"
        />
      </template>
    </ol>
  </nav>
</template>
