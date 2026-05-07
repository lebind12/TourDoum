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
      'animate-in slide-in-from-bottom-2 duration-300',
      variantClass,
    ]"
  >
    <span>{{ message }}</span>
    <button
      type="button"
      class="shrink-0 opacity-70 hover:opacity-100 transition-opacity"
      aria-label="닫기"
      @click="onDismiss?.()"
    >
      ✕
    </button>
  </div>
</template>
