<script setup lang="ts">
/**
 * AlertDialog — 위험 액션(탈퇴·삭제) 확인용 모달
 * @radix-vue 없이 순수 Vue + Teleport + focus-trap 구현
 *
 * Props:
 *   open: boolean        — v-model:open 으로 제어
 *   title: string        — 대화상자 제목 (필수)
 *   description?: string — 부가 설명
 *   confirmLabel?: string  — 확인 버튼 텍스트 (기본: '확인')
 *   cancelLabel?: string   — 취소 버튼 텍스트 (기본: '취소')
 *   variant?: 'destructive' | 'default' — 확인 버튼 컬러
 *   loading?: boolean    — 확인 버튼 로딩 상태
 *
 * Emits:
 *   update:open(value: boolean)
 *   confirm()
 *   cancel()
 *
 * 사용 예:
 *   <AlertDialog v-model:open="showDialog" title="회원 탈퇴" description="..." variant="destructive" @confirm="withdraw" />
 */
import { nextTick, onUnmounted, ref, watch } from "vue";

const props = withDefaults(
	defineProps<{
		open: boolean;
		title: string;
		description?: string;
		confirmLabel?: string;
		cancelLabel?: string;
		variant?: "destructive" | "default";
		loading?: boolean;
	}>(),
	{
		confirmLabel: "확인",
		cancelLabel: "취소",
		variant: "default",
		loading: false,
	},
);

const emit = defineEmits<{
	"update:open": [value: boolean];
	confirm: [];
	cancel: [];
}>();

const cancelBtnRef = ref<HTMLElement | null>(null);
// Round 6 (I): 직전 focus 요소 추적 — Esc/cancel/confirm 모든 close 경로에서 trigger로 복귀.
const previousFocusRef = ref<HTMLElement | null>(null);

watch(
	() => props.open,
	async (val) => {
		if (val) {
			previousFocusRef.value =
				document.activeElement instanceof HTMLElement
					? document.activeElement
					: null;
			await nextTick();
			cancelBtnRef.value?.focus();
		} else {
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
	},
);

// ESC 키로 닫기
function onKeydown(e: KeyboardEvent) {
	if (!props.open) return;
	if (e.key === "Escape") {
		handleCancel();
	}
	// Tab trap — 오버레이 안에서만 순환
	if (e.key === "Tab") {
		const dialog = document.getElementById("alert-dialog-panel");
		if (!dialog) return;
		const focusable = Array.from(
			dialog.querySelectorAll<HTMLElement>(
				'button:not([disabled]), [tabindex]:not([tabindex="-1"])',
			),
		);
		if (focusable.length === 0) return;
		const first = focusable[0];
		const last = focusable[focusable.length - 1];
		if (e.shiftKey && document.activeElement === first) {
			e.preventDefault();
			last.focus();
		} else if (!e.shiftKey && document.activeElement === last) {
			e.preventDefault();
			first.focus();
		}
	}
}

if (typeof window !== "undefined") {
	window.addEventListener("keydown", onKeydown);
	onUnmounted(() => window.removeEventListener("keydown", onKeydown));
}

function handleConfirm() {
	emit("confirm");
}

function handleCancel() {
	emit("cancel");
	emit("update:open", false);
}

function onOverlayClick(e: MouseEvent) {
	if ((e.target as HTMLElement).id === "alert-dialog-overlay") {
		handleCancel();
	}
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-150"
      leave-active-class="transition-opacity duration-150"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        id="alert-dialog-overlay"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="'alert-dialog-title'"
        :aria-describedby="description ? 'alert-dialog-description' : undefined"
        @click="onOverlayClick"
      >
        <div
          id="alert-dialog-panel"
          class="relative w-full max-w-sm rounded-lg bg-background p-6 shadow-xl ring-1 ring-border"
        >
          <!-- 제목 -->
          <h2
            id="alert-dialog-title"
            class="text-base font-semibold leading-snug"
            :class="variant === 'destructive' ? 'text-destructive' : 'text-foreground'"
          >
            {{ title }}
          </h2>

          <!-- 설명 -->
          <p
            v-if="description"
            id="alert-dialog-description"
            class="mt-2 text-sm text-muted-foreground"
          >
            {{ description }}
          </p>

          <!-- 슬롯 (추가 콘텐츠) -->
          <slot />

          <!-- 버튼 그룹 -->
          <div class="mt-5 flex justify-end gap-2">
            <!-- 취소 -->
            <button
              ref="cancelBtnRef"
              type="button"
              class="inline-flex items-center justify-center rounded-md border border-border bg-background px-4 py-2 text-sm font-medium
                     text-foreground shadow-sm transition-colors hover:bg-muted
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              :disabled="loading"
              @click="handleCancel"
            >
              {{ cancelLabel }}
            </button>

            <!-- 확인 -->
            <button
              type="button"
              class="inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium shadow-sm transition-colors
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
              :class="
                variant === 'destructive'
                  ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
                  : 'bg-primary text-primary-foreground hover:bg-primary/90'
              "
              :disabled="loading"
              :aria-busy="loading"
              @click="handleConfirm"
            >
              <span
                v-if="loading"
                class="mr-1.5 inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
                aria-hidden="true"
              />
              {{ confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
