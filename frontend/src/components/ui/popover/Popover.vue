<script setup lang="ts">
/**
 * Popover — 앵커 요소 근처에 떠오르는 패널 (알림 드로어 등)
 * @radix-vue 없이 순수 Vue + Teleport 구현
 *
 * 사용 예:
 *   <Popover v-model:open="open" align="end">
 *     <template #trigger>
 *       <Button @click="open = !open">열기</Button>
 *     </template>
 *     <template #content>
 *       <p>내용</p>
 *     </template>
 *   </Popover>
 *
 * Props:
 *   open: boolean        — v-model:open
 *   align?: 'start' | 'end' | 'center'  (기본: 'end')
 *   width?: string       — 패널 너비 (기본: 'w-72')
 *   offsetY?: number     — 트리거 아래 간격 px (기본: 8)
 *
 * Emits:
 *   update:open(value: boolean)
 */
import { nextTick, onUnmounted, ref, watch } from "vue";

const props = withDefaults(
	defineProps<{
		open: boolean;
		align?: "start" | "end" | "center";
		width?: string;
		offsetY?: number;
	}>(),
	{
		align: "end",
		width: "w-72",
		offsetY: 8,
	},
);

const emit = defineEmits<{
	"update:open": [value: boolean];
}>();

const triggerRef = ref<HTMLElement | null>(null);
const panelRef = ref<HTMLElement | null>(null);

// 패널 위치 계산
const panelStyle = ref<Record<string, string>>({});

watch(
	() => props.open,
	async (val) => {
		if (val) {
			await nextTick();
			computePosition();
			// 첫 번째 포커스 가능 요소에 포커스
			const first = panelRef.value?.querySelector<HTMLElement>(
				'button, [tabindex]:not([tabindex="-1"]), a[href], input',
			);
			first?.focus();
		}
	},
);

function computePosition() {
	const trigger = triggerRef.value;
	if (!trigger) return;
	const rect = trigger.getBoundingClientRect();
	const scrollY = window.scrollY;
	const scrollX = window.scrollX;

	const top = rect.bottom + scrollY + props.offsetY;

	if (props.align === "end") {
		panelStyle.value = {
			position: "absolute",
			top: `${top}px`,
			right: `${document.documentElement.clientWidth - rect.right - scrollX}px`,
		};
	} else if (props.align === "start") {
		panelStyle.value = {
			position: "absolute",
			top: `${top}px`,
			left: `${rect.left + scrollX}px`,
		};
	} else {
		const centerX = rect.left + rect.width / 2 + scrollX;
		panelStyle.value = {
			position: "absolute",
			top: `${top}px`,
			left: `${centerX}px`,
			transform: "translateX(-50%)",
		};
	}
}

// ESC 닫기 + Tab trap
function onKeydown(e: KeyboardEvent) {
	if (!props.open) return;
	if (e.key === "Escape") {
		emit("update:open", false);
		// 트리거로 포커스 복귀
		const btn = triggerRef.value?.querySelector<HTMLElement>("button");
		btn?.focus();
	}
}

if (typeof window !== "undefined") {
	window.addEventListener("keydown", onKeydown);
	onUnmounted(() => window.removeEventListener("keydown", onKeydown));
}
</script>

<template>
  <!-- 트리거 래퍼 -->
  <div ref="triggerRef" class="relative inline-flex">
    <slot name="trigger" />
  </div>

  <!-- 패널 (Teleport → body) -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-150 ease-out"
      leave-active-class="transition-all duration-100 ease-in"
      enter-from-class="opacity-0 translate-y-1 scale-95"
      leave-to-class="opacity-0 translate-y-1 scale-95"
    >
      <div
        v-if="open"
        ref="panelRef"
        :class="['z-50 rounded-xl border bg-popover shadow-lg', width]"
        :style="panelStyle"
        role="dialog"
        aria-modal="false"
      >
        <slot name="content" />
      </div>
    </Transition>

    <!-- 외부 클릭 오버레이 -->
    <div
      v-if="open"
      class="fixed inset-0 z-40"
      aria-hidden="true"
      @click="emit('update:open', false)"
    />
  </Teleport>
</template>
