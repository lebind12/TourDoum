import { onMounted } from "vue";

/**
 * 시스템 prefers-color-scheme에 따라 <html> 에 'dark' 클래스를 자동 적용한다.
 * 토글 UI는 미구현 — handoff.md 참조.
 */
export function useTheme() {
	onMounted(() => {
		const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");

		function applyTheme(isDark: boolean) {
			if (isDark) {
				document.documentElement.classList.add("dark");
			} else {
				document.documentElement.classList.remove("dark");
			}
		}

		applyTheme(mediaQuery.matches);

		mediaQuery.addEventListener("change", (e) => applyTheme(e.matches));
	});
}
