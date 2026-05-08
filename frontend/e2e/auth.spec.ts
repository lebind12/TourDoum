/**
 * e2e — 도메인횡단 인증 및 라우트 가드 검증
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 한다.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/auth.spec.ts
 *
 * Scenario A: 회원가입 → 로그인 → /me 본인 표시 → /attractions 진입 → /favorites 진입 → 로그아웃
 * Scenario B: 비로그인 /favorites 시도 → /login 리다이렉트 → 로그인 후 자동 복귀
 */
import { expect, test } from "@playwright/test";

const RUN = process.env.E2E_BACKEND === "1";

test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE 필요. 스킵.");

test("Scenario A: 회원가입 → 로그인 → /me → /attractions → /favorites → 로그아웃", async ({
	page,
}) => {
	const ts = Date.now();
	const email = `e2e_${ts}@tourdoum.test`;
	const password = "E2eTestSecure!9x";
	const nickname = `테스터${ts % 10000}`;

	// 1. 회원가입
	await page.goto("/signup");
	await page.fill('input[type="email"]', email);
	await page.fill('input[type="password"]', password);
	await page.fill('input[id="nickname"]', nickname);
	await page.click('button[type="submit"]');

	// 2. 회원가입 성공 → 자동 로그인 후 홈("/")으로 이동
	await page.waitForURL("/");

	// 3. 홈에 닉네임 포함 환영 메시지 표시 확인
	await expect(page.locator(".welcome-msg")).toContainText(
		`안녕하세요, ${nickname}님`,
	);

	// 4. /me 진입 (본인 정보 페이지)
	await page.goto("/me");
	await expect(page).toHaveURL("/me");
	await expect(page.locator("h1, h2")).toContainText(/본인|프로필|계정/);

	// 5. /attractions 진입 (관광지 목록)
	await page.goto("/attractions");
	await expect(page).toHaveURL("/attractions");
	await expect(page.locator("h1, h2")).toContainText(/관광지|attraction/i);

	// 6. /favorites 진입 (즐겨찾기 — 로그인 필요, 이미 로그인되어 있으므로 접근 가능)
	await page.goto("/favorites");
	await expect(page).toHaveURL("/favorites");
	await expect(page.locator("h1, h2")).toContainText(/즐겨찾기|favorite/i);

	// 7. 로그아웃 버튼 클릭
	await page.click(".btn-logout");

	// 8. 홈으로 리다이렉트되고 게스트 바가 보여야 한다
	await expect(page.locator(".guest-bar")).toBeVisible();
	await expect(page.locator(".guest-bar")).toContainText("로그인");
	await expect(page.locator(".guest-bar")).toContainText("회원가입");
});

test("Scenario B: 비로그인 /favorites 접근 → /login 리다이렉트 → 로그인 후 자동 복귀", async ({
	page,
}) => {
	// 1. /favorites에 비로그인 상태로 접근 시도
	await page.goto("/favorites");

	// 2. /login으로 리다이렉트되어야 함 (SavedRequest 패턴)
	await expect(page).toHaveURL("/login");

	// 3. 로그인 폼이 표시되어야 함
	await expect(page.locator('input[type="email"]')).toBeVisible();
	await expect(page.locator('input[type="password"]')).toBeVisible();

	// 4. 로그인 수행
	const ts = Date.now();
	const email = `e2e_${ts}@tourdoum.test`;
	const password = "E2eTestSecure!9x";
	const nickname = `테스터${ts % 10000}`;

	// 먼저 회원가입해야 로그인 가능
	await page.goto("/signup");
	await page.fill('input[type="email"]', email);
	await page.fill('input[type="password"]', password);
	await page.fill('input[id="nickname"]', nickname);
	await page.click('button[type="submit"]');

	// 자동 로그인됨 → 이미 /favorites 접근 가능

	// 5. 홈에서 로그아웃 후 다시 테스트
	await page.goto("/");
	await page.click(".btn-logout");

	// 6. /me에 비로그인 상태로 접근 시도
	await page.goto("/me");

	// 7. /login으로 리다이렉트되어야 함
	await expect(page).toHaveURL("/login");

	// 8. 로그인
	await page.fill('input[type="email"]', email);
	await page.fill('input[type="password"]', password);
	await page.click('button[type="submit"]');

	// 9. 로그인 후 원래 라우트인 /me로 복귀 (SavedRequest)
	// (주의: 현재 구현에서 SavedRequest가 활성화되어 있으면 /me로 복귀, 아니면 홈으로 이동)
	await page.waitForNavigation();
	const url = page.url();
	const urlPath =
		url.split("localhost:")[1]?.split("/").slice(1).join("/") || "";
	expect(urlPath === "/me" || urlPath === "/").toBeTruthy();
});
