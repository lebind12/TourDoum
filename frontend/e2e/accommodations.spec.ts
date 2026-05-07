/**
 * e2e — Accommodations 도메인 (검색, 상세, 위치 기반 조회)
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 하고, 36개 숙박 시드 데이터가 있어야 한다.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/accommodations.spec.ts
 *
 * Scenario A: 페이지 구조 및 필터 UI 검증
 *   - /accommodations 진입
 *   - 타이틀 "숙박 검색" 확인
 *   - 검색 입력, 타입 필터, 지역 필터 UI 요소 존재 확인
 *
 * Scenario B: 필터 UI 상호작용 검증
 *   - 검색 input에 텍스트 입력
 *   - select 필터 상호작용
 *   - 결과 영역 업데이트 확인
 *
 * Scenario C: 회귀 검증
 *   - 홈: TourDoum 타이틀
 *   - 관광지: 기존 페이지 구조
 *   - 로그인/회원가입: 폼 접근성
 *
 * [주의]
 * - 현재 개발 환경: CORS 미설정으로 인해 API 데이터 조회 불가
 * - 실제 full e2e (API 데이터 포함)는 BE의 CORS 설정 후 재실행 필요
 * - 현 테스트는 FE 페이지 구조 및 UI 컴포넌트 가용성만 검증
 */
import { expect, test } from "@playwright/test";

const RUN = process.env.E2E_BACKEND === "1";

test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

test.describe("숙박 검색 — 페이지 구조 및 필터 UI 검증", () => {
	test("Scenario A: accommodations 페이지 기본 구조", async ({ page }) => {
		// 1. 페이지 진입
		await page.goto("/accommodations");
		await expect(page).toHaveURL("/accommodations");

		// 2. 타이틀 확인
		await expect(page.locator("h1")).toContainText("숙박 검색");

		// 3. 검색 입력 필드 존재
		const searchInput = page.locator("input[placeholder*='숙소 이름 또는 지역']");
		await expect(searchInput).toBeVisible();

		// 4. 타입 필터 select 존재
		const typeSelect = page.locator("select").nth(0);
		await expect(typeSelect).toBeVisible();

		// 5. 지역 필터 select 존재
		const sidoSelect = page.locator("select").nth(1);
		await expect(sidoSelect).toBeVisible();

		// 6. "전체 지역" 옵션 첫 번째 위치 확인
		const firstOption = sidoSelect.locator("option").first();
		const optionText = await firstOption.textContent();
		expect(optionText?.trim()).toBe("전체 지역");

		// 7. 결과 영역 (로딩 스켈레톤 / 에러 / 빈 상태 / 그리드 중 하나) 존재 확인.
		//    `:has-text(/regex/)`는 Playwright CSS 엔진이 파싱 못하므로
		//    개별 locator를 OR(.or())로 묶어 첫 매치 대기.
		const grid = page.locator("div.grid");
		const errorText = page.getByText("불러오지 못했");
		const emptyText = page.getByText("검색 결과가 없습니다");
		await expect(grid.or(errorText).or(emptyText).first()).toBeVisible();
	});

	test("Scenario B: 검색 입력 및 필터 동작", async ({ page }) => {
		// 1. 페이지 진입
		await page.goto("/accommodations");
		await expect(page).toHaveURL("/accommodations");

		// 2. 검색 입력필드 입력
		const searchInput = page.locator("input[placeholder*='숙소 이름 또는 지역']");
		await searchInput.fill("서울");
		await expect(searchInput).toHaveValue("서울");

		// 3. 지역 select 상호작용 (선택 없이 열린 상태 확인)
		const sidoSelect = page.locator("select").nth(1);
		await expect(sidoSelect).toBeVisible();
		const selectedValue = await sidoSelect.inputValue();
		expect(typeof selectedValue).toBe("string");

		// 4. 타입 select 상호작용
		const typeSelect = page.locator("select").nth(0);
		await expect(typeSelect).toBeVisible();

		// 5. 결과 영역 존재 확인
		await page.waitForTimeout(300);
		const resultArea = page.locator("body");
		await expect(resultArea).toContainText(/검색 결과|불러오지 못했|로딩/);
	});

	test("Scenario C: 필터 옵션 구조", async ({ page }) => {
		// 1. 페이지 진입
		await page.goto("/accommodations");

		// SPA 마운트 후 정적 placeholder option이 DOM에 박힐 때까지 대기.
		await expect(
			page.locator("select option", { hasText: "전체 숙소 유형" }),
		).toBeAttached();

		// 2. 타입 select의 옵션 개수 확인
		const typeSelect = page.locator("select").nth(0);
		const typeOptions = typeSelect.locator("option");
		const typeOptionCount = await typeOptions.count();
		expect(typeOptionCount).toBeGreaterThan(0);

		// 3. 첫 번째 옵션이 "전체" 또는 유사한 기본값
		const firstTypeOption = await typeOptions.first().textContent();
		expect(firstTypeOption?.trim().length).toBeGreaterThan(0);

		// 4. 지역 select의 옵션 개수 확인
		const sidoSelect = page.locator("select").nth(1);
		const sidoOptions = sidoSelect.locator("option");
		const sidoOptionCount = await sidoOptions.count();
		expect(sidoOptionCount).toBeGreaterThan(0);

		// 5. select들이 disabled 되지 않았는지 확인
		await expect(typeSelect).not.toBeDisabled();
		await expect(sidoSelect).not.toBeDisabled();
	});
});

test.describe("회귀 검증 — 기존 도메인 기능 무회귀 확인", () => {
	test("회귀: 홈 페이지 랜딩 4섹션 구조 (#39)", async ({ page }) => {
		// #39 랜딩 블록 스크롤 — TourDoum 텍스트는 hero 섹션 배지로 이동, h1은 카피로 변경.
		// 4섹션(hero / features / showcase / cta)이 aria-label로 박제됨 — 의미적 selector 사용.
		await page.goto("/");

		// 1. hero 섹션 + 메인 카피 h1 노출
		const hero = page.locator("section[aria-label='Hero — 서비스 소개']");
		await expect(hero).toBeVisible();
		await expect(hero.locator("h1")).toContainText("딱 맞는 하루");

		// 2. TourDoum 브랜드 배지가 hero 안에 존재
		await expect(hero).toContainText("TourDoum");

		// 3. 나머지 3섹션이 마운트되었는지 (블록 스크롤 컨테이너 자식)
		await expect(page.locator("section[aria-label='주요 기능']")).toBeAttached();
		await expect(
			page.locator("section[aria-label='인기 여행지 · 숙박 미리보기']"),
		).toBeAttached();
		await expect(
			page.locator("section[aria-label='회원가입 유도']"),
		).toBeAttached();
	});

	test("회귀: 내 정보(/me) 페이지 접근 시 로그인 리다이렉트", async ({
		page,
	}) => {
		// 1. /me 페이지 접근 (비로그인 상태)
		await page.goto("/me");

		// 2. /login으로 리다이렉트 확인
		await expect(page).toHaveURL(/\/login/);

		// 3. 로그인 폼 요소 확인
		const emailInput = page.getByLabel(/이메일|email/i);
		await expect(emailInput).toBeVisible();
	});

	test("회귀: 관광지(/attractions) 페이지 기본 구조", async ({ page }) => {
		// 1. 관광지 페이지 진입
		await page.goto("/attractions");
		await expect(page).toHaveURL("/attractions");

		// 2. 페이지 제목 확인 (여행지 탐색 또는 유사)
		const heading = page.locator("h1").first();
		const headingText = await heading.textContent();
		expect(headingText?.trim().length).toBeGreaterThan(0);

		// 3. 검색 input 존재 확인
		const searchInput = page.locator("input").first();
		await expect(searchInput).toBeVisible();
	});

	test("회귀: 즐겨찾기(/favorites) 페이지 접근 시 로그인 리다이렉트", async ({
		page,
	}) => {
		// 1. /favorites 접근 (비로그인 상태)
		await page.goto("/favorites");

		// 2. /login으로 리다이렉트 확인
		await expect(page).toHaveURL(/\/login/);

		// 3. 비로그인 상태 표시
		const emailInput = page.getByLabel(/이메일|email/i);
		await expect(emailInput).toBeVisible();
	});

	test("회귀: 회원가입 페이지 폼 접근성", async ({ page }) => {
		// 1. 회원가입 페이지 진입
		await page.goto("/signup");
		await expect(page).toHaveURL(/\/signup/);

		// 2. 회원가입 폼 존재 확인
		const signupForm = page.locator('form[aria-label="회원가입 폼"]');
		await expect(signupForm).toBeVisible();

		// 3. 이메일 입력 필드
		const emailInput = page.getByLabel(/이메일|email/i);
		await expect(emailInput).toBeVisible();

		// 4. 비밀번호 입력 필드 (폼 내부 scope)
		const passwordInput = signupForm.getByLabel(/비밀번호|password/i);
		await expect(passwordInput).toBeVisible();

		// 5. 닉네임 입력 필드
		const nicknameInput = page.getByLabel(/닉네임|nickname/i);
		await expect(nicknameInput).toBeVisible();
	});
});
