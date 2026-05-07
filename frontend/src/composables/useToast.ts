import { reactive, readonly } from "vue";

export type ToastVariant = "default" | "success" | "error" | "warning";

export interface ToastItem {
	id: number;
	message: string;
	variant: ToastVariant;
	duration: number;
}

interface ToastState {
	items: ToastItem[];
}

const state = reactive<ToastState>({ items: [] });
let _nextId = 0;

function toast(
	message: string,
	variant: ToastVariant = "default",
	duration = 3000,
) {
	const id = _nextId++;
	state.items.push({ id, message, variant, duration });

	setTimeout(() => {
		dismiss(id);
	}, duration);
}

function dismiss(id: number) {
	const idx = state.items.findIndex((t) => t.id === id);
	if (idx !== -1) state.items.splice(idx, 1);
}

export function useToast() {
	return {
		toasts: readonly(state.items),
		toast,
		dismiss,
		/** 편의 메서드 */
		success: (msg: string, duration?: number) =>
			toast(msg, "success", duration),
		error: (msg: string, duration?: number) => toast(msg, "error", duration),
		warning: (msg: string, duration?: number) =>
			toast(msg, "warning", duration),
	};
}
