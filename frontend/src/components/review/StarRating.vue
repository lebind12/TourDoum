<script setup lang="ts">
/**
 * StarRating — 별점 표시/입력 컴포넌트
 * mode='display': 읽기 전용, mode='input': 클릭 선택
 */
const props = withDefaults(
	defineProps<{
		modelValue: number;
		mode?: "display" | "input";
		max?: number;
	}>(),
	{
		mode: "display",
		max: 5,
	},
);

const emit = defineEmits<{
	"update:modelValue": [value: number];
}>();

function select(n: number) {
	if (props.mode === "input") {
		emit("update:modelValue", n);
	}
}
</script>

<template>
  <span
    class="inline-flex items-center gap-0.5"
    :role="mode === 'input' ? 'group' : 'img'"
    :aria-label="`별점 ${modelValue}점 / ${max}점`"
  >
    <button
      v-for="n in max"
      :key="n"
      :type="mode === 'input' ? 'button' : undefined"
      :disabled="mode !== 'input'"
      :aria-label="mode === 'input' ? `${n}점` : undefined"
      class="leading-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm"
      :class="[
        mode === 'input' ? 'cursor-pointer hover:scale-110 transition-transform' : 'cursor-default pointer-events-none'
      ]"
      @click="select(n)"
    >
      <span
        class="text-lg"
        :class="n <= modelValue ? 'text-amber-400' : 'text-muted-foreground/30'"
        aria-hidden="true"
      >★</span>
    </button>
    <span v-if="mode === 'display'" class="ml-1 text-sm font-medium text-foreground">
      {{ modelValue.toFixed(1) }}
    </span>
  </span>
</template>
