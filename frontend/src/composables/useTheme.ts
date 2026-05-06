import { onMounted, onUnmounted, ref } from "vue";

type ThemeMode = "auto" | "light" | "dark";

const STORAGE_KEY = "tourdoum-theme";

/**
 * 테마 모드 관리 composable.
 * - auto: 시스템 prefers-color-scheme 따름
 * - light / dark: 사용자 수동 override (localStorage 저장)
 */
export function useTheme() {
	const mode = ref<ThemeMode>(
		(localStorage.getItem(STORAGE_KEY) as ThemeMode | null) ?? "auto",
	);

	let mediaQuery: MediaQueryList | null = null;

	function applyDark(isDark: boolean) {
		if (isDark) {
			document.documentElement.classList.add("dark");
		} else {
			document.documentElement.classList.remove("dark");
		}
	}

	function applyMode(m: ThemeMode) {
		if (m === "dark") {
			applyDark(true);
		} else if (m === "light") {
			applyDark(false);
		} else {
			applyDark(mediaQuery?.matches ?? false);
		}
	}

	function handleSystemChange(e: MediaQueryListEvent) {
		if (mode.value === "auto") {
			applyDark(e.matches);
		}
	}

	function setTheme(m: ThemeMode) {
		mode.value = m;
		if (m === "auto") {
			localStorage.removeItem(STORAGE_KEY);
		} else {
			localStorage.setItem(STORAGE_KEY, m);
		}
		applyMode(m);
	}

	onMounted(() => {
		mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
		mediaQuery.addEventListener("change", handleSystemChange);
		applyMode(mode.value);
	});

	onUnmounted(() => {
		mediaQuery?.removeEventListener("change", handleSystemChange);
	});

	return { mode, setTheme };
}
