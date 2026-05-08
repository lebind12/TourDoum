/**
 * e2e — Favorites 도메인 (즐겨찾기 추가/제거 + 가드)
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 하고, attraction 시드가 있어야 한다.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/favorites.spec.ts
 *
 * Scenario A: 비로그인 /favorites → /login 리다이렉트 (가드 회귀)
 * Scenario B: 로그인 직후 /favorites 빈 상태
 * Scenario C: /attractions에서 즐겨찾기 추가 → /favorites에 노출
 * Scenario D: /favorites에서 제거 → 빈 상태 복귀
 *
 * [selector 정책]
 * - signup form submit: data-testid="signup-submit" (AppShell의 "회원가입" 버튼과 충돌 회피)
 * - FavoriteButton aria-label: 토글 상태에 따라 "즐겨찾기 추가" / "즐겨찾기 제거"
 * - FavoritesView 제거 버튼 aria-label: `${name} 즐겨찾기 제거` (개별 카드 식별)
 *
 * [BE/FE 범위]
 * - BE는 ATTRACTION + ACCOMMODATION 양방향 즐겨찾기 지원.
 * - FE는 현재 ATTRACTION만 store/UI 노출 → 본 spec도 ATTRACTION만 검증.
 *
 * [#18 패턴 재사용]
 * - origin host = localhost (playwright.config.ts E2E_HOST)
 * - SPA 마운트 race → toBeVisible({ timeout }) 명시적 대기
 * - regex `:has-text` 금지 → .or() 체이닝
 */
import { type Page, expect, test } from "@playwright/test";

const RUN = process.env.E2E_BACKEND === "1";

test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

/**
 * 신규 계정 signup → 자동 로그인 헬퍼.
 * 매 테스트별 unique 계정으로 즐겨찾기 격리.
 * 세 번째 spec(#22 Reviews 등)에서도 사용되면 `e2e/_helpers/auth.ts`로 추출 권고.
 */
async function signupAndLogin(page: Page): Promise<{
	email: string;
	nickname: string;
}> {
	const ts = Date.now();
	const rand = Math.floor(Math.random() * 10000);
	const email = `qa-fav-${ts}-${rand}@example.com`;
	const password = "E2eTestSecure!9x";
	const nickname = `qaf${ts}${rand}`.slice(0, 20);

	await page.goto("/signup");
	await page.getByLabel(/이메일|email/i).fill(email);
	await page
		.locator('form[aria-label="회원가입 폼"]')
		.getByLabel(/비밀번호|password/i)
		.fill(password);
	await page.getByLabel(/닉네임|nickname/i).fill(nickname);
	await page.getByTestId("signup-submit").click();

	// signup 성공 시 / 로 리다이렉트 + 닉네임 노출.
	// 닉네임은 AppShell 헤더 span + HomeView 환영 카드 strong 양쪽에 렌더되므로
	// strict mode 충돌 회피 위해 .first() 한정.
	await expect(page).toHaveURL("/");
	await expect(
		page.getByText(new RegExp(`.*${nickname}.*`)).first(),
	).toBeVisible();

	return { email, nickname };
}

test.describe("즐겨찾기 — 가드 + CRUD", () => {
	test("Scenario A: 비로그인 /favorites 접근 → /login 리다이렉트", async ({
		page,
	}) => {
		await page.goto("/favorites");
		await expect(page).toHaveURL(/\/login/);
		// 로그인 폼 노출 확인
		await expect(page.getByLabel(/이메일|email/i)).toBeVisible();
	});

	test("Scenario B: 로그인 직후 /favorites 빈 상태", async ({ page }) => {
		await signupAndLogin(page);

		await page.goto("/favorites");
		await expect(page).toHaveURL("/favorites");

		// 헤더 확인
		await expect(page.locator("h1")).toContainText("즐겨찾기");
		// 빈 상태 메시지 노출
		await expect(
			page.getByText("아직 즐겨찾기한 여행지가 없어요"),
		).toBeVisible();
		// 카운트 = 0
		await expect(page.getByText(/내가 저장한 여행지\s*0\s*곳/)).toBeVisible();
	});

	test("Scenario C: /attractions에서 즐겨찾기 추가 → /favorites에 노출", async ({
		page,
	}) => {
		await signupAndLogin(page);

		// /attractions 진입 + attraction 카드 로딩 대기
		await page.goto("/attractions");
		await expect(page).toHaveURL("/attractions");

		// AttractionsView는 flex 컬럼 레이아웃(grid 미사용) → FavoriteButton 등장을
		// 카드 로딩 완료 신호로 사용. 에러 상태는 "불러오지 못했" 텍스트로 폴백.
		const addButton = page
			.locator('button[aria-label="즐겨찾기 추가"]')
			.first();
		const errorText = page.getByText("불러오지 못했");
		await expect(addButton.or(errorText)).toBeVisible({ timeout: 10000 });
		await expect(addButton).toBeVisible();

		// 첫 카드의 이름 캡처 — addButton과 같은 카드(Card 부모)의 h2.
		// Card 컴포넌트 구조: Card > <RouterLink> > Card > [img, CardContent[h2, ...FavoriteButton]]
		// 카드 단위 컨테이너가 명시적이지 않으므로 페이지 전체에서 카드 h2 첫 번째 사용.
		const firstCardName = await page.locator("h2").first().textContent();
		expect(firstCardName?.trim().length).toBeGreaterThan(0);

		// 토글 추가 (RouterLink 안에 있지만 FavoriteButton은 @click.prevent로 막음)
		await addButton.click();

		// /favorites로 이동
		await page.goto("/favorites");
		await expect(page).toHaveURL("/favorites");

		// 빈 상태 메시지가 사라지고 그리드 노출
		await expect(page.getByText("아직 즐겨찾기한 여행지가 없어요")).toHaveCount(
			0,
		);
		await expect(page.locator("div.grid").first()).toBeVisible({
			timeout: 5000,
		});

		// 카운트 = 1 + 첫 카드 이름이 목록에 노출
		await expect(page.getByText(/내가 저장한 여행지\s*1\s*곳/)).toBeVisible();
		const trimmedName = (firstCardName ?? "").trim();
		await expect(
			page.locator("div.grid h2", { hasText: trimmedName }).first(),
		).toBeVisible();
	});

	test("Scenario D: /favorites에서 제거 → 빈 상태 복귀", async ({ page }) => {
		await signupAndLogin(page);

		// 사전 조건: /attractions에서 1건 추가 (AttractionsView는 grid 미사용 → button으로 대기)
		await page.goto("/attractions");
		const addButton = page
			.locator('button[aria-label="즐겨찾기 추가"]')
			.first();
		const errorText = page.getByText("불러오지 못했");
		await expect(addButton.or(errorText)).toBeVisible({ timeout: 10000 });
		await addButton.click();

		// /favorites 진입 → 1건 노출 확인
		await page.goto("/favorites");
		await expect(page.locator("div.grid").first()).toBeVisible({
			timeout: 5000,
		});
		await expect(page.getByText(/내가 저장한 여행지\s*1\s*곳/)).toBeVisible();

		// 제거 버튼 클릭 (`${name} 즐겨찾기 제거` aria-label)
		const removeButton = page
			.locator('button[aria-label$="즐겨찾기 제거"]')
			.first();
		await expect(removeButton).toBeVisible();
		await removeButton.click();

		// 빈 상태 복귀 + 카운트 = 0
		await expect(
			page.getByText("아직 즐겨찾기한 여행지가 없어요"),
		).toBeVisible();
		await expect(page.getByText(/내가 저장한 여행지\s*0\s*곳/)).toBeVisible();
	});
});
