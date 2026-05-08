/**
 * e2e — SPA navigation helper (qa #32 — page.goto 카스케이드 해소).
 *
 * 배경 (ADR-0011):
 * - access/refresh token 은 메모리 보관(localStorage 금지). full-page reload (page.goto) 시
 *   토큰 소실 → fetchMe 401 → /login 리다이렉트 카스케이드.
 * - signup/login 후 모든 페이지 전환은 SPA navigation(router) 으로 수행.
 *
 * 패턴:
 * - **navigateTo(path)**: 가능하면 nav-bar 클릭(실 사용자 동선), 매핑 없으면
 *   `history.pushState + popstate` 조합으로 Vue Router 의 popstate 리스너 트리거.
 *   둘 다 SPA 내부 라우팅이므로 store/메모리 토큰 보존.
 * - 직접 진입(deep-link) 동선 검증이 목적인 spec 은 본 helper 사용 부적합 — 별도 처리.
 *
 * 사용:
 *   import { navigateTo } from "./_helpers/nav";
 *   await signupAndLogin(page);
 *   await navigateTo(page, "/me");  // page.goto 대신
 */
import { type Page, expect } from "@playwright/test";

/**
 * SPA navigation. 가능하면 실 사용자 클릭, 매핑 없으면 history API 로 SPA push.
 *
 * @param page Playwright Page
 * @param path target 라우트 (e.g. "/me", "/plans/plan-001", "/reservations/new/1/dates")
 */
export async function navigateTo(page: Page, path: string): Promise<void> {
	// 0. 페이지 미로드 (about:blank) 시 page.goto fallback — auth 영향 없음(첫 진입).
	if (page.url() === "about:blank" || page.url() === "") {
		await page.goto(path);
		return;
	}

	// 1. SPA push — Vue Router 의 push API 를 직접 호출.
	//    이전 구현은 nav-bar role=link 클릭 또는 `window.history.pushState({}, "", p)` 직접 호출이었으나:
	//      (a) 로그아웃 직후 AppShell re-render 와 race 하여 link click 이 detached element 로 실패 (qa #5 auth.spec B)
	//      (b) `pushState({}, ...)` 가 vue-router 의 historyState (back/current/forward 형태) 를 빈 `{}` 로
	//          덮어써, 후속 router.push 가 `currentState.current = undefined` 를 직렬화하다
	//          `Failed to execute 'replace' on 'Location': 'origin' + 'undefined' + path` 회귀 (qa #5 plans Scenario B)
	//    →  단일 경로: 모듈 dynamic import 로 router.push 호출. requiresAuth 가드도 동일하게 통과.
	//    waitForURL 은 router.push 의 동기 직후가 아니라 popstate 처리 완료까지 polling 한다.
	await page.evaluate(async (p) => {
		const m = await import("/src/router/index.ts");
		const r = (m as { default: { push: (to: string) => Promise<void> } })
			.default;
		await r.push(p);
	}, path);
	// 보호 라우트 가드가 /login 으로 리다이렉트할 수 있으므로, 목표 path 또는 /login 둘 중 하나를 허용.
	// `page.waitForURL` 의 기본 waitUntil 이 "load" 라 SPA pushState 에서는 load 이벤트가 발화하지
	// 않아 timeout 한다 (qa #5 auth.spec B 회귀). `expect(page).toHaveURL` 는 URL 만 polling 한다.
	await expect(page).toHaveURL(
		new RegExp(`(${escapeRegExp(path)}/?$|/login(\\?.*)?$)`),
		{ timeout: 5000 },
	);
}

/** "/" 진입 — RouterLink "TourDoum" 브랜드 클릭. */
export async function navigateHome(page: Page): Promise<void> {
	await page.getByRole("link", { name: "TourDoum" }).first().click();
	await expect(page).toHaveURL(/\/$/);
}

function escapeRegExp(s: string): string {
	return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
