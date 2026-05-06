<script setup lang="ts">
/**
 * Sheet — 슬라이드 드로어 (shadcn-vue Sheet 호환 인터페이스, radix-vue 미사용)
 * side: 드로어가 나타나는 방향 (left | right)
 */
interface Props {
	open?: boolean;
	side?: "left" | "right";
}

const props = withDefaults(defineProps<Props>(), {
	open: false,
	side: "left",
});

const emit = defineEmits<{
	"update:open": [value: boolean];
}>();

function close() {
	emit("update:open", false);
}
</script>

<template>
  <!-- Overlay -->
  <Transition name="sheet-overlay">
    <div
      v-if="open"
      class="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm"
      aria-hidden="true"
      @click="close"
    />
  </Transition>

  <!-- Panel -->
  <Transition :name="side === 'right' ? 'sheet-right' : 'sheet-left'">
    <div
      v-if="open"
      role="dialog"
      aria-modal="true"
      :class="[
        'fixed inset-y-0 z-50 flex flex-col bg-background shadow-xl',
        side === 'right' ? 'right-0 w-3/4 max-w-xs border-l border-border' : 'left-0 w-3/4 max-w-xs border-r border-border',
      ]"
    >
      <slot />
    </div>
  </Transition>
</template>

<style scoped>
/* Overlay fade */
.sheet-overlay-enter-active,
.sheet-overlay-leave-active {
  transition: opacity 0.2s ease;
}
.sheet-overlay-enter-from,
.sheet-overlay-leave-to {
  opacity: 0;
}

/* Left slide */
.sheet-left-enter-active,
.sheet-left-leave-active {
  transition: transform 0.25s ease;
}
.sheet-left-enter-from,
.sheet-left-leave-to {
  transform: translateX(-100%);
}

/* Right slide */
.sheet-right-enter-active,
.sheet-right-leave-active {
  transition: transform 0.25s ease;
}
.sheet-right-enter-from,
.sheet-right-leave-to {
  transform: translateX(100%);
}
</style>
