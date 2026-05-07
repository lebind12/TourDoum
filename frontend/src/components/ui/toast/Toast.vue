<script setup lang="ts">
import type { ToastVariant } from "@/composables/useToast";
import { computed } from "vue";

interface Props {
	message: string;
	variant?: ToastVariant;
	onDismiss?: () => void;
}

const props = withDefaults(defineProps<Props>(), {
	variant: "default",
});

const variantClass = computed(() => {
	switch (props.variant) {
		case "success":
			return "bg-success text-success-foreground";
		case "error":
			return "bg-destructive text-destructive-foreground";
		case "warning":
			return "bg-warning text-warning-foreground";
		default:
			return "bg-card text-card-foreground border border-border";
	}
});
</script>

<template>
  <div
    role="status"
    aria-live="polite"
    :class="[
      'flex items-center justify-between gap-3 rounded-lg px-4 py-3 shadow-lg text-sm font-medium',
      // Round 8 (M): duration-300 hardcode → R7 motion-base 토큰. easing은 emphasized로 살짝 오버슈트.
      'animate-in slide-in-from-bottom-2 duration-[var(--motion-base)] ease-[var(--ease-emphasized)]',
      variantClass,
    ]"
  >
    <span>{{ message }}</span>
    <button
      type="button"
      class="shrink-0 opacity-70 hover:opacity-100 transition-opacity duration-[var(--motion-fast)] ease-[var(--ease-standard)]"
      aria-label="닫기"
      @click="onDismiss?.()"
    >
      ✕
    </button>
  </div>
</template>
