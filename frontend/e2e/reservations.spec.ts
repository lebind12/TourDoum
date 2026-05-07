/**
 * e2e — Reservations 도메인 (3단계 흐름 + Idempotency-Key 멱등성)
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이고 accommodation 시드 1개 이상.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/reservations.spec.ts
 *
 * Scenario A: 비로그인 → /reservations/new/.../dates → /login 리다이렉트 (가드)
 * Scenario B: 3단계 정상 흐름 — dates → payment → complete
 * Scenario C: Idempotency-Key 멱등성 — 동일 키 + body 2회 → 같은 reservationId
 * Scenario D: 완료 후 /me "내 예약" 섹션에 노출 + 새로고침 잔존
 *
 * [도메인 메모]
 * - dispatch의 "투숙객 정보" 단계는 실제 도메인에 없음 (Stepper: 날짜·인원 → 결제 수단 → 예약 완료)
 * - /reservations 단독 list 라우트 없음 — /me 페이지 "내 예약" 섹션 사용
 *   (handoff §결정 포인트 참조)
 * - BE: ReservationController.confirm — 동일 Idempotency-Key 재요청 시 200 + 기존 예약 반환
 *   (ReservationService.findByIdempotencyKey + UNIQUE INDEX)
 *
 * [#22 헬퍼 재사용]
 * `import { signupAndLogin } from "./_helpers/auth"` — 본 회차에서 첫 외부 import 활용.
 */
import { type Page, expect, test } from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";

const RUN = process.env.E2E_BACKEND === "1";

/**
 * 첫 accommodation ID를 BE API에서 직접 캡처.
 * (AccommodationDetailView가 detail API 응답에 누락 필드 접근 시 부분 렌더되어
 *  "예약하기" 버튼까지 도달 불가 — handoff §결정 포인트 참조. spec은 detail UI를
 *  거치지 않고 /reservations 라우트로 직접 진입.)
 */
async function getFirstAccommodationId(page: Page): Promise<number> {
	const r = await page.request.get(
		"http://localhost:8080/api/accommodations?size=1",
	);
	expect(r.ok()).toBeTruthy();
	const body = await r.json();
	const id = body?.content?.[0]?.id;
	expect(id).toBeGreaterThan(0);
	return id;
}

/** YYYY-MM-DD 포맷 (n일 후) */
function dateAfter(days: number): string {
	const d = new Date();
	d.setDate(d.getDate() + days);
	return d.toISOString().slice(0, 10);
}

test.describe("예약 — 3단계 흐름 + 멱등성 + 회귀", () => {
	test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

	test("Scenario A: 비로그인 /reservations/new/1/dates → /login 리다이렉트", async ({
		page,
	}) => {
		await page.goto("/reservations/new/1/dates");
		await expect(page).toHaveURL(/\/login/);
	});

	test("Scenario B: UI 흐름 — Step 1(dates) → Step 2(payment) + 예약 확정 API 성공", async ({
		page,
	}) => {
		await signupAndLogin(page, "qares");
		const accommodationId = await getFirstAccommodationId(page);

		// detail UI 우회 — /reservations/new/:id/dates 직접 진입 (handoff 참조)
		await page.goto(`/reservations/new/${accommodationId}/dates`);
		await expect(page).toHaveURL(
			new RegExp(`/reservations/new/${accommodationId}/dates`),
		);

		// Step 1: 날짜·인원
		await expect(page.locator("h1")).toContainText("날짜");
		await page.locator("#check-in").fill(dateAfter(1));
		await page.locator("#check-out").fill(dateAfter(3));
		// 성인/아동 기본값 1/0 사용

		// aria-label="결제 수단 선택으로 이동" — 텍스트 "다음 단계 — 결제 수단"보다 우선
		await page.getByRole("button", { name: "결제 수단 선택으로 이동" }).click();

		// Step 2: 결제 수단
		await expect(page).toHaveURL(
			new RegExp(`/reservations/new/${accommodationId}/payment`),
		);
		await expect(page.locator("h1")).toContainText("결제");

		// 결제 수단 기본값 사용. "예약 확인" 버튼 (aria-label="예약 확인 및 결제")
		const confirmBtn = page.getByRole("button", {
			name: "예약 확인 및 결제",
		});
		await expect(confirmBtn).toBeEnabled();

		// network 응답 wait + 클릭. POST /api/reservations 200/201 OK 확인 후
		// /reservations/new/:id/complete/:reservationId URL 도달까지 검증
		// (fe #49 라우터 가드 fix 회귀).
		const respPromise = page.waitForResponse(
			(r) =>
				r.url().includes("/api/reservations") &&
				!r.url().includes("/quote") &&
				!r.url().includes("/me") &&
				r.request().method() === "POST",
			{ timeout: 15000 },
		);
		await confirmBtn.click();
		const resp = await respPromise;
		expect(resp.ok()).toBeTruthy();
		const body = await resp.json();
		expect(body.id).toBeTruthy();

		// fe #49 회귀: 가드 통과 후 complete URL 진입.
		await expect(page).toHaveURL(
			new RegExp(`/reservations/new/${accommodationId}/complete/${body.id}`),
		);
	});

	test("Scenario C: Idempotency-Key 멱등성 — 같은 키 2회 → 같은 reservationId", async ({
		page,
	}) => {
		await signupAndLogin(page, "qares");
		const accommodationId = await getFirstAccommodationId(page);

		// 인증 쿠키가 page 컨텍스트에 박혀있으므로 page.request로 BE에 직접 요청.
		const checkIn = dateAfter(5);
		const checkOut = dateAfter(7);
		const idempotencyKey = crypto.randomUUID();

		// 0. 견적 (UNIQUE INDEX는 confirm 단계에서 검증되므로 quote 생략 가능하지만
		//    BE 일부 구현은 quote 선행을 요구할 수 있어 안전상 호출).
		await page.request.post("http://localhost:8080/api/reservations/quote", {
			data: {
				accommodationId,
				checkIn,
				checkOut,
				guests: 2,
			},
		});

		const body = {
			accommodationId,
			checkIn,
			checkOut,
			guests: 2,
			paymentMethod: "CARD",
		};

		// 1차 호출 — 신규 INSERT 기대
		const r1 = await page.request.post(
			"http://localhost:8080/api/reservations",
			{
				headers: { "Idempotency-Key": idempotencyKey },
				data: body,
			},
		);
		expect(r1.ok()).toBeTruthy();
		const j1 = await r1.json();
		expect(j1.id).toBeTruthy();

		// 2차 호출 — 같은 키 + 같은 body → 기존 예약 반환.
		// BE 컨트롤러는 신규/기존 모두 201 반환 (ReservationController.confirm은
		// HttpStatus.CREATED 고정). 멱등성은 reservationId 동일성으로 검증.
		const r2 = await page.request.post(
			"http://localhost:8080/api/reservations",
			{
				headers: { "Idempotency-Key": idempotencyKey },
				data: body,
			},
		);
		expect(r2.ok()).toBeTruthy();
		const j2 = await r2.json();
		expect(j2.id).toBe(j1.id);
	});

	test("Scenario D: 예약 1건 생성 후 /me '내 예약' 섹션에 노출 + 새로고침 잔존", async ({
		page,
	}) => {
		await signupAndLogin(page, "qares");
		const accommodationId = await getFirstAccommodationId(page);

		// API 직접 호출로 예약 1건 생성 (UI 라우터 가드 버그 우회 — handoff 참조).
		// 인증 쿠키는 page 컨텍스트에 자동 전파.
		const checkIn = dateAfter(10);
		const checkOut = dateAfter(12);
		await page.request.post("http://localhost:8080/api/reservations/quote", {
			data: { accommodationId, checkIn, checkOut, guests: 1 },
		});
		const create = await page.request.post(
			"http://localhost:8080/api/reservations",
			{
				headers: { "Idempotency-Key": crypto.randomUUID() },
				data: {
					accommodationId,
					checkIn,
					checkOut,
					guests: 1,
					paymentMethod: "CARD",
				},
			},
		);
		expect(create.ok()).toBeTruthy();

		// /me 진입 → "내 예약" 섹션 노출 + 1건 이상 (BE에서 fetchMyReservations)
		await page.goto("/me");
		const section = page.locator(
			'section[aria-labelledby="my-reservations-heading"]',
		);
		await expect(section).toBeVisible({ timeout: 5000 });
		// "예약 없음" 메시지가 사라져야 함 (현재 신규 계정 + 1건 생성 직후)
		await expect(section.getByText("아직 예약한 숙소가 없습니다.")).toHaveCount(
			0,
		);

		// 새로고침 후에도 잔존
		await page.reload();
		await expect(section).toBeVisible({ timeout: 5000 });
		await expect(section.getByText("아직 예약한 숙소가 없습니다.")).toHaveCount(
			0,
		);
	});
});
