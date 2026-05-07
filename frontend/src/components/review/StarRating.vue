<script setup lang="ts">
/**
 * StarRating — 별점 표시/입력 컴포넌트 (lucide Star 아이콘 + hover 인터랙션)
 * mode='display': 읽기 전용, mode='input': 클릭 선택 + hover 프리뷰
 *
 * Props:
 *   modelValue: number
 *   mode?: 'display' | 'input'
 *   max?: number
 *   size?: 'sm' | 'md' | 'lg'
 */
import { Star } from "lucide-vue-next";
import { ref } from "vue";

const props = withDefaults(
	defineProps<{
		modelValue: number;
		mode?: "display" | "input";
		max?: number;
		size?: "sm" | "md" | "lg";
	}>(),
	{
		mode: "display",
		max: 5,
		size: "md",
	},
);

const emit = defineEmits<{
	"update:modelValue": [value: number];
}>();

// hover 프리뷰 (input 모드에서만)
const hoverValue = ref(0);

function onMouseEnter(n: number) {
	if (props.mode === "input") hoverValue.value = n;
}

function onMouseLeave() {
	hoverValue.value = 0;
}

function select(n: number) {
	if (props.mode === "input") {
		emit("update:modelValue", n);
	}
}

const sizeMap = {
	sm: 14,
	md: 18,
	lg: 22,
} as const;

// 표시할 채움 기준 (hover 중에는 hover값 우선)
function isFilled(n: number) {
	const base = hoverValue.value > 0 ? hoverValue.value : props.modelValue;
	return n <= base;
}
</script>

<template>
  <span
    class="inline-flex items-center gap-0.5"
    :role="mode === 'input' ? 'group' : 'img'"
    :aria-label="`별점 ${modelValue}점 / ${max}점`"
    @mouseleave="onMouseLeave"
  >
    <button
      v-for="n in max"
      :key="n"
      :type="mode === 'input' ? 'button' : undefined"
      :disabled="mode !== 'input'"
      :tabindex="mode === 'input' ? 0 : -1"
      :aria-label="mode === 'input' ? `${n}점` : undefined"
      :aria-pressed="mode === 'input' ? modelValue >= n : undefined"
      class="leading-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm transition-transform"
      :class="[
        mode === 'input'
          ? 'cursor-pointer hover:scale-125 active:scale-110'
          : 'cursor-default pointer-events-none',
      ]"
      @click="select(n)"
      @mouseenter="onMouseEnter(n)"
    >
      <Star
        :size="sizeMap[size]"
        aria-hidden="true"
        :class="[
          'transition-colors duration-100',
          isFilled(n)
            ? 'fill-amber-400 stroke-amber-400'
            : 'fill-transparent stroke-muted-foreground/40',
          mode === 'input' && hoverValue >= n ? 'stroke-amber-300' : '',
        ]"
        :stroke-width="1.5"
      />
    </button>

    <!-- 점수 텍스트 (display 모드) -->
    <span
      v-if="mode === 'display'"
      class="ml-1 text-sm font-medium tabular-nums text-foreground"
    >
      {{ modelValue.toFixed(1) }}
    </span>
  </span>
</template>
