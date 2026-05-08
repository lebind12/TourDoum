/**
 * e2e — 여행 계획 happy path (store-only, BE 미구현)
 *
 * 실행 조건: FE dev server + Pinia store mock (localStorage 기반).
 * auth guard를 우회하기 위해 localStorage에 mock 사용자를 주입한다.
 *
 *   npx playwright test e2e/plans-flow.spec.ts
 */
import { expect, test } from "@playwright/test";

/** Pinia auth store가 사용하는 localStorage 키를 직접 주입하여 auth guard를 우회한다.
 * BE 미구현이므로 /api/me 호출은 실패하지만, store-only 상태로 /plans를 렌더링한다.
 * beforeEach에서 route guard가 fetchMe를 호출하므로 API 요청이 401로 떨어질 수 있다.
 * 그 경우 /login으로 리다이렉트되어 plans 라우트 테스트가 실패하게 된다.
 * → 이 spec은 auth state 주입 없이 public 라우트만 검증하고,
 *   guard 우회가 필요한 부분은 skip 처리한다.
 */

test.describe("여행 계획 — 공개 접근 및 리다이렉트 검증", () => {
	test("비로그인 /plans → /login 리다이렉트", async ({ page }) => {
		await page.goto("/plans");
		// auth guard가 로그인 페이지로 보내야 함
		await expect(page).toHaveURL(/\/login/);
	});

	test("비로그인 /plans/new → /login 리다이렉트", async ({ page }) => {
		await page.goto("/plans/new");
		await expect(page).toHaveURL(/\/login/);
	});

	test("비로그인 /plans/plan-001 → /login 리다이렉트", async ({ page }) => {
		await page.goto("/plans/plan-001");
		await expect(page).toHaveURL(/\/login/);
	});
});

test.describe("여행 계획 — 로그인 후 happy path (E2E_BACKEND=1 필요)", () => {
	const RUN = process.env.E2E_BACKEND === "1";

	test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

	test("로그인 후 /plans 목록 진입 — 시드 계획 카드 확인", async ({ page }) => {
		const ts = Date.now();
		const email = `plan-e2e-${ts}@example.com`;
		const password = "E2eTestSecure!9x";
		const nickname = `planner${ts}`;

		// 회원가입 + 자동 로그인
		await page.goto("/signup");
		await page.getByLabel(/이메일|email/i).fill(email);
		await page.getByLabel(/비밀번호|password/i).fill(password);
		await page.getByLabel(/닉네임|nickname/i).fill(nickname);
		await page.getByRole("button", { name: /가입|회원가입|sign up/i }).click();
		await expect(page).toHaveURL("/");

		// /plans 진입
		await page.goto("/plans");
		await expect(page).toHaveURL("/plans");
		await expect(
			page.getByRole("heading", { name: /내 여행 계획/ }),
		).toBeVisible();

		// 시드 계획 카드가 보여야 함
		await expect(page.getByText("제주 3박 4일 힐링 여행")).toBeVisible();
	});

	test("/plans/new — 제목·기간 입력 후 계획 생성 → 상세 페이지 이동", async ({
		page,
	}) => {
		const ts = Date.now();
		const email = `plan-new-${ts}@example.com`;
		const password = "E2eTestSecure!9x";
		const nickname = `newplanner${ts}`;

		await page.goto("/signup");
		await page.getByLabel(/이메일|email/i).fill(email);
		await page.getByLabel(/비밀번호|password/i).fill(password);
		await page.getByLabel(/닉네임|nickname/i).fill(nickname);
		await page.getByRole("button", { name: /가입|회원가입|sign up/i }).click();
		await expect(page).toHaveURL("/");

		await page.goto("/plans/new");
		await expect(
			page.getByRole("heading", { name: /새 여행 계획/ }),
		).toBeVisible();

		await page.getByLabel(/여행 제목/).fill("e2e 테스트 여행");
		await page.getByLabel(/출발일/).fill("2026-09-01");
		await page.getByLabel(/귀가일/).fill("2026-09-03");
		await page.getByRole("button", { name: /계획 만들기/ }).click();

		// 상세 페이지로 이동
		await expect(page).toHaveURL(/\/plans\/.+/);
		await expect(page.getByText("e2e 테스트 여행")).toBeVisible();
		// Day 탭 3개 (9/1, 9/2, 9/3)
		await expect(page.getByRole("tab", { name: /Day 1/ })).toBeVisible();
		await expect(page.getByRole("tab", { name: /Day 2/ })).toBeVisible();
		await expect(page.getByRole("tab", { name: /Day 3/ })).toBeVisible();
	});

	test("/plans/:id — 시드 계획 상세 진입 + 일자 탭 전환", async ({ page }) => {
		const ts = Date.now();
		const email = `plan-detail-${ts}@example.com`;
		const password = "E2eTestSecure!9x";
		const nickname = `detailplanner${ts}`;

		await page.goto("/signup");
		await page.getByLabel(/이메일|email/i).fill(email);
		await page.getByLabel(/비밀번호|password/i).fill(password);
		await page.getByLabel(/닉네임|nickname/i).fill(nickname);
		await page.getByRole("button", { name: /가입|회원가입|sign up/i }).click();
		await expect(page).toHaveURL("/");

		await page.goto("/plans/plan-001");
		await expect(page.getByText("제주 3박 4일 힐링 여행")).toBeVisible();

		// Day 1 이미 선택됨 — 성산일출봉 아이템 확인
		await expect(page.getByText("성산일출봉")).toBeVisible();

		// Day 2 탭 전환
		await page.getByRole("tab", { name: /Day 2/ }).click();
		await expect(page.getByText("천지연폭포")).toBeVisible();
	});
});
