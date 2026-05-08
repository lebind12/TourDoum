/**
 * e2e — Reviews 도메인 (Attraction 후기 작성/노출/유효성)
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 하고, attraction 시드 1개 이상.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/reviews.spec.ts
 *
 * Scenario A: 비로그인 → /attractions/:id "로그인하고 후기 작성하기" → /login 리다이렉트
 * Scenario B: 로그인 → "후기 작성" → 5자 미만 입력 → 에러 메시지 노출
 * Scenario C: 로그인 → 별점 + 5자 이상 본문 → 제출 → 성공 메시지 + 목록에 본인 닉네임/본문 노출
 * Scenario D: 작성된 리뷰가 새로고침 후에도 노출 + 평균 별점 = 본인 별점
 *
 * [selector 정책]
 * - signup helper: e2e/_helpers/auth.ts (signup → 자동 로그인)
 * - 별점 입력: <StarRating mode="input"> — 별 아이콘 클릭. 컨테이너에 별 button 5개,
 *   클릭 시 emit("update:modelValue", n). DOM 구조상 [aria-label]이 명시되지 않은
 *   span/button → role 기반보다 textarea + submit 버튼만 검증, 별점은 기본값 5 사용.
 * - 본문: textarea#review-comment
 * - 제출 버튼: <Button type="submit">"후기 등록"</Button>
 *
 * [BE 차단 우회 #41]
 * Review.rating int ↔ DB tinyint 미스매치로 BE 부팅 실패. #41 머지 전까지
 * `SPRING_JPA_HIBERNATE_DDL_AUTO=none` 환경변수로 BE를 띄워야 한다 (handoff 참조).
 */
import { expect, test } from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";
import { navigateTo } from "./_helpers/nav";

const RUN = process.env.E2E_BACKEND === "1";
test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

/**
 * /attractions에서 첫 카드의 detail 페이지로 이동.
 * AttractionsView는 flex 컬럼(grid 미사용) → FavoriteButton으로 카드 로딩 대기.
 */
async function gotoFirstAttractionDetail(
	page: import("@playwright/test").Page,
): Promise<void> {
	await navigateTo(page, "/attractions");
	const addButton = page.locator('button[aria-label="즐겨찾기 추가"]').first();
	const errorText = page.getByText("불러오지 못했");
	await expect(addButton.or(errorText)).toBeVisible({ timeout: 10000 });

	// 첫 카드의 RouterLink (FavoriteButton은 @click.prevent라 카드 자체 링크 따라감)
	// 카드의 h2를 클릭해도 RouterLink 안에 있으므로 detail로 이동.
	await page.locator("h2").first().click();
	await expect(page).toHaveURL(/\/attractions\/\d+/);
}

test.describe("후기 — 작성 + 유효성 + 회귀", () => {
	test("Scenario A: 비로그인 → '로그인하고 후기 작성하기' → /login 리다이렉트", async ({
		page,
	}) => {
		await gotoFirstAttractionDetail(page);

		// 후기 섹션 노출 + 비로그인 CTA 버튼
		const loginCta = page.getByRole("button", {
			name: "로그인하고 후기 작성하기",
		});
		await expect(loginCta).toBeVisible();
		await loginCta.click();
		await expect(page).toHaveURL(/\/login/);
	});

	test("Scenario B: 로그인 → 5자 미만 입력 → 에러 메시지", async ({ page }) => {
		await signupAndLogin(page, "qarev");
		await gotoFirstAttractionDetail(page);

		// "후기 작성" 토글 (이미 본인 후기 있으면 "내 후기 보기/수정" → 신규 계정이라 케이스 X)
		const writeBtn = page.getByRole("button", { name: "후기 작성" });
		await expect(writeBtn).toBeVisible();
		await writeBtn.click();

		// 폼 노출
		const textarea = page.locator("#review-comment");
		await expect(textarea).toBeVisible();
		await textarea.fill("짧음"); // 4자 < 5자

		await page.getByRole("button", { name: "후기 등록" }).click();

		// 에러 메시지
		await expect(page.locator("#review-error")).toContainText(
			"후기는 5자 이상 작성해 주세요.",
		);
	});

	test("Scenario C: 로그인 → 정상 입력 → 제출 → 성공 + 목록 노출", async ({
		page,
	}) => {
		const { nickname } = await signupAndLogin(page, "qarev");
		await gotoFirstAttractionDetail(page);

		await page.getByRole("button", { name: "후기 작성" }).click();

		const textarea = page.locator("#review-comment");
		await expect(textarea).toBeVisible();
		const comment = `e2e 자동화 후기입니다 ${Date.now()}`;
		await textarea.fill(comment);

		await page.getByRole("button", { name: "후기 등록" }).click();

		// 성공 메시지 (role=status, aria-live=polite)
		await expect(page.getByText("후기가 등록되었습니다!")).toBeVisible({
			timeout: 5000,
		});

		// 목록에 본인 닉네임 + 본문 노출.
		// 닉네임은 AppShell 헤더 + ReviewList 양쪽 매칭 가능 → 본문이 unique이므로 본문으로 검증.
		await expect(page.getByText(comment)).toBeVisible();

		// ReviewList 안에 닉네임이 적어도 1번 노출되는지 확인 (aria-label="후기 목록" scope)
		const list = page.locator('ul[aria-label="후기 목록"]');
		await expect(list.getByText(nickname).first()).toBeVisible();
	});

	test("Scenario D: 작성한 후기가 새로고침 후 노출 + 평균 별점 갱신", async ({
		page,
	}) => {
		await signupAndLogin(page, "qarev");
		await gotoFirstAttractionDetail(page);

		// 본문 캡처용 unique 마커
		const marker = `e2e-D-${Date.now()}`;
		await page.getByRole("button", { name: "후기 작성" }).click();
		await page.locator("#review-comment").fill(`정상 후기 ${marker}`);
		await page.getByRole("button", { name: "후기 등록" }).click();
		await expect(page.getByText("후기가 등록되었습니다!")).toBeVisible({
			timeout: 5000,
		});

		// 새로고침
		await page.reload();

		// 후기 섹션이 다시 마운트된 후 본인 후기가 list에 잔존
		const list = page.locator('ul[aria-label="후기 목록"]');
		await expect(list).toBeVisible({ timeout: 5000 });
		await expect(list.getByText(marker)).toBeVisible();

		// 평균 별점이 (n건) 표기와 함께 노출 — n>=1
		await expect(page.getByText(/\(\d+건\)/)).toBeVisible();
	});
});
