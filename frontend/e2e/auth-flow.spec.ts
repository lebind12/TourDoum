/**
 * e2e — 도메인횡단 인증 및 라우트 가드 검증
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 한다.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/auth-flow.spec.ts
 *
 * Scenario A: 회원가입 → 자동 로그인 → 홈 닉네임 → /me → /attractions → /favorites → 로그아웃
 * Scenario B: 비로그인 /favorites → /login 리다이렉트
 * Scenario C: 비로그인 /me → /login 리다이렉트
 *
 * [selector 정책]
 * AppShell 헤더/드로어에 "회원가입" <Button>(role=button)이 있어
 * getByRole('button', { name: /회원가입/ })가 strict mode에서 복수 매칭 → 오류.
 * 폼 submit 버튼에 data-testid를 부여하고 getByTestId()로 단일 특정한다.
 *   - signup form submit → data-testid="signup-submit"  (SignupView.vue)
 *   - login  form submit → data-testid="login-submit"   (LoginView.vue)
 */
import { expect, test } from "@playwright/test";
import { navigateTo } from "./_helpers/nav";

const RUN = process.env.E2E_BACKEND === "1";

test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

test.describe("도메인횡단 인증 및 가드 검증", () => {
	test("Scenario A: signup → 자동 login → home 닉네임 표시 → /me → /attractions → /favorites → logout", async ({
		page,
	}) => {
		const ts = Date.now();
		const email = `qa-${ts}@example.com`;
		const password = "E2eTestSecure!9x";
		const nickname = `qa${ts}`;

		await page.goto("/signup");
		await page.getByLabel(/이메일|email/i).fill(email);
		// 비밀번호 라벨이 "현재 비밀번호"와 겹치지 않도록 회원가입 폼 내부로 scope
		await page
			.locator('form[aria-label="회원가입 폼"]')
			.getByLabel(/비밀번호|password/i)
			.fill(password);
		await page.getByLabel(/닉네임|nickname/i).fill(nickname);
		// [fix] AppShell nav/drawer 버튼과 충돌 방지 — data-testid 사용
		await page.getByTestId("signup-submit").click();

		await expect(page).toHaveURL("/");
		await expect(
			page.getByText(new RegExp(`.*${nickname}.*`)).first(),
		).toBeVisible();

		await navigateTo(page, "/me");
		await expect(page).toHaveURL("/me");
		await expect(page.locator("h1, h2, body")).toContainText(
			/본인|프로필|계정|profile|account|me/i,
		);

		await navigateTo(page, "/attractions");
		await expect(page).toHaveURL("/attractions");
		await expect(page.locator("h1, h2, body")).toContainText(
			/관광지|attraction|여행/i,
		);

		await navigateTo(page, "/favorites");
		await expect(page).toHaveURL("/favorites");
		await expect(page.locator("h1, h2, body")).toContainText(
			/즐겨찾기|favorite/i,
		);

		// 로그아웃 버튼은 헤더에만 존재 → 충돌 없음
		await page
			.getByRole("button", { name: /로그아웃|logout/i })
			.first()
			.click();

		await expect(page).toHaveURL("/");
		// 로그아웃 후 nav에 버튼 형태 "로그인" / "회원가입"이 나타남
		await expect(
			page.getByRole("button", { name: /로그인|login/i }).first(),
		).toBeVisible();
		await expect(
			page.getByRole("button", { name: /회원가입|sign up/i }).first(),
		).toBeVisible();
	});

	test("Scenario B: 비로그인 /favorites 접근 → /login 리다이렉트 확인", async ({
		page,
	}) => {
		await navigateTo(page, "/favorites");

		await expect(page).toHaveURL(/\/login/);
		await expect(page.getByLabel(/이메일|email/i)).toBeVisible();
		await expect(
			page
				.locator('form[aria-label="로그인 폼"]')
				.getByLabel(/비밀번호|password/i),
		).toBeVisible();
	});

	test("Scenario C: 비로그인 /me 접근 → /login 리다이렉트 확인", async ({
		page,
	}) => {
		await navigateTo(page, "/me");

		await expect(page).toHaveURL(/\/login/);
		await expect(page.getByLabel(/이메일|email/i)).toBeVisible();
		await expect(
			page
				.locator('form[aria-label="로그인 폼"]')
				.getByLabel(/비밀번호|password/i),
		).toBeVisible();
	});
});
