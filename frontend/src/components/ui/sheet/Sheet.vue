<script setup lang="ts">
/**
 * Sheet — 슬라이드 드로어 (shadcn-vue Sheet 호환 인터페이스, radix-vue 미사용)
 * side: 드로어가 나타나는 방향 (left | right)
 *
 * Round 6 (I) 보강:
 * - role="dialog" aria-modal="true"
 * - Esc 키로 닫기
 * - focus trap (panelRef 안에서 Tab/Shift-Tab 순환)
 * - 열릴 때 첫 focusable 요소에 focus, 닫힐 때 직전 focus 요소로 복귀
 *   (overlay click, Esc, 부모가 open=false 모든 경로 커버 — close()에 넣지 않고 watch(open))
 */
import { nextTick, onUnmounted, ref, watch } from "vue";

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

const panelRef = ref<HTMLElement | null>(null);
const previousFocusRef = ref<HTMLElement | null>(null);

const FOCUSABLE_SELECTOR =
	'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function getFocusable(root: HTMLElement | null): HTMLElement[] {
	if (!root) return [];
	return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
}

watch(
	() => props.open,
	async (open) => {
		if (open) {
			previousFocusRef.value =
				document.activeElement instanceof HTMLElement
					? document.activeElement
					: null;
			await nextTick();
			const focusable = getFocusable(panelRef.value);
			if (focusable.length > 0) {
				focusable[0].focus();
			} else {
				panelRef.value?.focus();
			}
		} else {
			restoreFocus();
		}
	},
);

function restoreFocus() {
	const el = previousFocusRef.value;
	previousFocusRef.value = null;
	if (
		el?.isConnected &&
		!el.hasAttribute("disabled") &&
		typeof el.focus === "function"
	) {
		el.focus({ preventScroll: true });
	}
}

function close() {
	emit("update:open", false);
}

function onKeydown(e: KeyboardEvent) {
	if (!props.open) return;
	if (e.key === "Escape") {
		e.preventDefault();
		close();
		return;
	}
	if (e.key === "Tab") {
		const focusable = getFocusable(panelRef.value);
		if (focusable.length === 0) {
			e.preventDefault();
			panelRef.value?.focus();
			return;
		}
		const first = focusable[0];
		const last = focusable[focusable.length - 1];
		const active = document.activeElement;
		if (e.shiftKey && active === first) {
			e.preventDefault();
			last.focus();
		} else if (!e.shiftKey && active === last) {
			e.preventDefault();
			first.focus();
		}
	}
}

if (typeof window !== "undefined") {
	window.addEventListener("keydown", onKeydown);
	onUnmounted(() => window.removeEventListener("keydown", onKeydown));
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
      ref="panelRef"
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      :class="[
        'fixed inset-y-0 z-50 flex flex-col bg-background shadow-xl focus:outline-none',
        side === 'right' ? 'right-0 w-3/4 max-w-xs border-l border-border' : 'left-0 w-3/4 max-w-xs border-r border-border',
      ]"
    >
      <slot />
    </div>
  </Transition>
</template>

<style scoped>
/* Round 8 (M): R7 모션 토큰(--motion-base / --ease-emphasized) 일원화.
 * 이전 hardcode (0.2s/0.25s ease) 제거 — 토큰 변경이 자동 전파. */

/* Overlay fade */
.sheet-overlay-enter-active,
.sheet-overlay-leave-active {
  transition: opacity var(--motion-base) var(--ease-standard);
}
.sheet-overlay-enter-from,
.sheet-overlay-leave-to {
  opacity: 0;
}

/* Left slide */
.sheet-left-enter-active,
.sheet-left-leave-active {
  transition: transform var(--motion-base) var(--ease-emphasized);
}
.sheet-left-enter-from,
.sheet-left-leave-to {
  transform: translateX(-100%);
}

/* Right slide */
.sheet-right-enter-active,
.sheet-right-leave-active {
  transition: transform var(--motion-base) var(--ease-emphasized);
}
.sheet-right-enter-from,
.sheet-right-leave-to {
  transform: translateX(100%);
}
</style>
