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

/** AppShell 데스크톱 nav-bar 에 노출되는 라우트 → role=button 텍스트. */
const NAV_BAR_LABELS: Record<string, string> = {
	"/attractions": "여행지",
	"/accommodations": "숙박",
	"/chat": "채팅",
	"/favorites": "즐겨찾기",
	"/me": "내 정보",
	"/login": "로그인",
	"/signup": "회원가입",
};

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
	// 1. nav-bar 매핑이 있으면 클릭 (실 사용자 동선 우선).
	const label = NAV_BAR_LABELS[path];
	if (label) {
		// AppShell 데스크톱 + 모바일 drawer 에 동일 텍스트 → .first()
		await page.getByRole("link", { name: label, exact: true }).first().click();
		await page.waitForURL(new RegExp(`${escapeRegExp(path)}/?$`));
		return;
	}

	// 2. 매핑 없으면 SPA push. Vue Router 4(createWebHistory)는 popstate 리스너로
	//    history 변경을 감지해 라우트를 업데이트한다.
	await page.evaluate((p) => {
		window.history.pushState({}, "", p);
		window.dispatchEvent(new PopStateEvent("popstate", { state: {} }));
	}, path);
	await page.waitForURL(new RegExp(`${escapeRegExp(path)}/?$`));
}

/** "/" 진입 — RouterLink "TourDoum" 브랜드 클릭. */
export async function navigateHome(page: Page): Promise<void> {
	await page.getByRole("link", { name: "TourDoum" }).first().click();
	await expect(page).toHaveURL(/\/$/);
}

function escapeRegExp(s: string): string {
	return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
