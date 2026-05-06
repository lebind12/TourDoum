/**
 * e2e — 회원가입 → 로그인 → /me 확인 → 로그아웃 플로우
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 한다.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/auth.spec.ts
 */
import { expect, test } from "@playwright/test";

const RUN = process.env.E2E_BACKEND === "1";

test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE 필요. 스킵.");

test("회원가입 → 자동 로그인 → 홈 닉네임 확인 → 로그아웃", async ({ page }) => {
	const ts = Date.now();
	const email = `e2e_${ts}@tourdoum.test`;
	const password = "Password1!";
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

	// 4. 로그아웃 버튼 클릭
	await page.click(".btn-logout");

	// 5. 홈에 게스트 링크가 보여야 한다
	await expect(page.locator(".guest-bar")).toBeVisible();
	await expect(page.locator(".guest-bar")).toContainText("로그인");
	await expect(page.locator(".guest-bar")).toContainText("회원가입");
});
