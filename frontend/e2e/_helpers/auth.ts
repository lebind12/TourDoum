/**
 * e2e 공통 인증 헬퍼.
 *
 * #18, #20에서 spec 내부에 inline 반복되던 signup → 자동 로그인 패턴을
 * 본 #22에서 첫 추출. 이후 #24~#30에서 재사용한다.
 *
 * [selector 정책]
 * - `data-testid="signup-submit"` (SignupView.vue) — AppShell의 "회원가입"
 *   <Button>(role=button)과 strict mode 충돌 회피.
 * - 닉네임 텍스트는 AppShell 헤더 <span> + HomeView 환영카드 <strong> 양쪽에
 *   렌더되므로 검증은 항상 `.first()` 한정.
 *
 * 사용 예:
 *   import { signupAndLogin } from "./_helpers/auth";
 *   const { nickname } = await signupAndLogin(page);
 */
import { type Page, expect } from "@playwright/test";

export interface SignupResult {
	email: string;
	password: string;
	nickname: string;
}

/**
 * 매 테스트별 unique 신규 계정 signup → 자동 로그인.
 * 성공 시 / 로 리다이렉트되고 닉네임이 화면에 노출된다.
 *
 * @param page Playwright Page
 * @param prefix 닉네임 prefix (영문, 알파벳/숫자만 권장). 충돌 회피용 timestamp 자동 부여.
 */
export async function signupAndLogin(
	page: Page,
	prefix = "qa",
): Promise<SignupResult> {
	const ts = Date.now();
	const rand = Math.floor(Math.random() * 10000);
	const email = `${prefix}-${ts}-${rand}@example.com`;
	const password = "password1";
	// 닉네임은 영문/숫자, 길이 제한 회피 위해 20자로 절단
	const nickname = `${prefix}${ts}${rand}`.slice(0, 20);

	await page.goto("/signup");
	await page.getByLabel(/이메일|email/i).fill(email);
	// "현재 비밀번호" 등과 충돌 회피 — 회원가입 폼 내부로 scope
	await page
		.locator('form[aria-label="회원가입 폼"]')
		.getByLabel(/비밀번호|password/i)
		.fill(password);
	await page.getByLabel(/닉네임|nickname/i).fill(nickname);
	// AppShell nav/drawer "회원가입" 버튼과 충돌 회피 — testid 사용
	await page.getByTestId("signup-submit").click();

	// signup 성공 시 / 로 리다이렉트 + 닉네임 노출.
	// 헤더 span(데스크톱) + HomeView strong 양쪽 매칭 → strict mode 회피 .first()
	await expect(page).toHaveURL("/");
	await expect(
		page.getByText(new RegExp(`.*${nickname}.*`)).first(),
	).toBeVisible();

	return { email, password, nickname };
}
