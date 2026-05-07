/**
 * e2e — Notifications 도메인 (트리거 + 읽음 처리)
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE는 SERVER_PORT=30080(.env.agent), FE는 E2E_PORT=30174.
 *
 *   set -a; . .env.agent; set +a
 *   E2E_BACKEND=1 npx playwright test e2e/notifications.spec.ts
 *
 * Scenario A: 비로그인 → /notifications → /login 리다이렉트 (router meta requiresAuth)
 * Scenario B: signup+login → 예약 confirm API 트리거 → /notifications 카드 노출 + unread badge ≥ 1
 * Scenario C: 단건 읽음 — 카드 클릭 → POST /{id}/read → 미읽음 점 사라짐 + unread count 감소
 * Scenario D: 전체 읽음 — "전체 읽음" 버튼 클릭 → POST /read-all → unread count 0 + 버튼 사라짐
 *
 * [BE 트리거]
 * `ReservationService.confirm()` 내부에서 `notificationService.publish(
 *   RESERVATION_CONFIRMED, "예약이 확정되었습니다", "<숙소명> 예약이 ...", "/reservations/me")` 호출.
 * → 본 spec은 dates/payment UI 우회 후 `POST /api/reservations` 직접 호출
 *   (qa #24 idempotency 패턴과 동일).
 *
 * [JWT Bearer 흐름 + SPA navigation]
 * - `auth-token`은 module closure 보관(ADR-0011: 페이지 새로고침 시 소실 = "다시 로그인" UX 정책).
 * - 따라서 본 spec은 `signupAndLogin` 직후 page.goto("/notifications") 같은 hard-reload를
 *   **금지**한다. 대신 AppShell `data-testid="notification-bell"` Popover의
 *   "전체 알림 보기" RouterLink를 click 해 SPA push 로 진입 → 인증 상태 유지.
 * - reservation API 트리거는 page.request(BE 직접) 인데, FE 인터셉터를 거치지 않으므로
 *   raw accessToken 이 필요. → `/api/auth/login`을 page.request로 한 번 더 호출해 token 회수.
 *   (UI 헬퍼가 이미 새 family를 만들었지만 본 spec 범위는 단일 회차이므로 무방.)
 * - BE-3 cookie 전환 시 `loginApi` 부분 적응 필요 (QA-1 후속).
 *
 * [#22 헬퍼 재사용]
 * `import { signupAndLogin } from "./_helpers/auth"` — qa #22에서 추출한 공용 인증 헬퍼.
 */
import { type Page, expect, test } from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";

const RUN = process.env.E2E_BACKEND === "1";

const BACKEND_URL = process.env.VITE_API_BASE_URL ?? "http://localhost:30080";

const SCREENSHOT_DIR = "e2e/screenshots";

/** YYYY-MM-DD 포맷 (n일 후) */
function dateAfter(days: number): string {
	const d = new Date();
	d.setDate(d.getDate() + days);
	return d.toISOString().slice(0, 10);
}

async function getFirstAccommodationId(page: Page): Promise<number> {
	const r = await page.request.get(`${BACKEND_URL}/api/accommodations?size=1`);
	expect(r.ok()).toBeTruthy();
	const body = await r.json();
	const id = body?.content?.[0]?.id;
	expect(id).toBeGreaterThan(0);
	return id;
}

/**
 * BE-3 CSRF double-submit token 회수.
 *
 * `CookieCsrfTokenRepository.withHttpOnlyFalse()` 가 매 응답에 `XSRF-TOKEN` cookie 를
 * 박는다. mutation(POST/PUT/DELETE) 시 동일 값을 `X-XSRF-TOKEN` 헤더로 함께 보내야 한다.
 * 면제: `/api/auth/login`, `/api/members/signup` (사전인증 단계).
 *
 * 호출 순서:
 *   1. 임의 GET (CsrfCookieFilter가 cookie set).
 *   2. `page.context().cookies(BACKEND_URL)` 로 XSRF-TOKEN 추출.
 *   3. 후속 page.request POST에 `X-XSRF-TOKEN: <token>` 헤더 동봉.
 */
async function getCsrfToken(page: Page): Promise<string> {
	// Cookie 박제 유도 (이미 set 됐어도 ok)
	const r = await page.request.get(`${BACKEND_URL}/api/health`);
	// page.request 는 자체 쿠키 jar 를 가짐 — Set-Cookie 헤더에서 직접 추출.
	const setCookie = r.headers()["set-cookie"];
	if (setCookie) {
		const m = setCookie.match(/XSRF-TOKEN=([^;\s,]+)/);
		if (m?.[1] && m[1].length > 1) return m[1];
	}
	const cookies = await page.context().cookies(BACKEND_URL);
	const xsrf = cookies.find((c) => c.name === "XSRF-TOKEN" && c.value);
	expect(xsrf, "XSRF-TOKEN cookie 미설정 — BE-3 csrf 설정 확인").toBeTruthy();
	if (!xsrf) throw new Error("unreachable");
	return xsrf.value;
}

/** raw accessToken 회수 — page.request에서 Authorization Bearer 헤더 직접 부여용. */
async function loginApi(
	page: Page,
	email: string,
	password: string,
): Promise<string> {
	const r = await page.request.post(`${BACKEND_URL}/api/auth/login`, {
		data: { email, password },
	});
	expect(r.ok()).toBeTruthy();
	const body = await r.json();
	expect(body.accessToken).toBeTruthy();
	return body.accessToken as string;
}

/** Reservation 1건 trigger — RESERVATION_CONFIRMED 알림 publish 유도. */
async function triggerReservation(
	page: Page,
	accessToken: string,
	accommodationId: number,
	daysOffset = 1,
): Promise<number> {
	const checkIn = dateAfter(daysOffset);
	const checkOut = dateAfter(daysOffset + 2);
	const csrf = await getCsrfToken(page);
	// page.request는 cross-origin Cookie jar 동기화가 다소 모호 — Cookie 헤더 명시 부여.
	const headers = {
		Authorization: `Bearer ${accessToken}`,
		"X-XSRF-TOKEN": csrf,
		Cookie: `XSRF-TOKEN=${csrf}`,
	};

	await page.request.post(`${BACKEND_URL}/api/reservations/quote`, {
		headers,
		data: { accommodationId, checkIn, checkOut, guests: 1 },
	});

	const create = await page.request.post(`${BACKEND_URL}/api/reservations`, {
		headers: { ...headers, "Idempotency-Key": crypto.randomUUID() },
		data: {
			accommodationId,
			checkIn,
			checkOut,
			guests: 1,
			paymentMethod: "CARD",
		},
	});
	if (!create.ok()) {
		const debug = await create.text().catch(() => "<no body>");
		throw new Error(
			`triggerReservation failed: status=${create.status()} body=${debug}`,
		);
	}
	expect(create.ok()).toBeTruthy();
	const body = await create.json();
	expect(body.id).toBeTruthy();
	return body.id as number;
}

/**
 * SPA navigation: 헤더 종 아이콘(testid="notification-bell") Popover → "전체 알림 보기"
 * RouterLink click → /notifications.
 *
 * page.goto는 module-scope JWT를 잃으므로 금지. Popover Trigger + RouterLink 조합만 사용.
 * 진입할 때마다 NotificationsView.onMounted 가 fetchNotifications 를 다시 부르므로 최신 목록 보장.
 */
async function navigateToNotifications(page: Page): Promise<void> {
	// AppShell에 desktop + mobile NotificationDrawer 가 모두 마운트 → testid 2개.
	// 데스크톱 nav(첫 번째)만 클릭.
	await page.getByTestId("notification-bell").first().click();
	await page
		.getByRole("link", { name: /전체 알림 보기/ })
		.first()
		.click();
	await expect(page).toHaveURL(/\/notifications$/);
	await expect(page.locator("h1").filter({ hasText: "알림" })).toBeVisible();
}

/** /notifications 에서 home(/) 로 SPA 복귀 — 다음 진입 시 onMounted refire 유도. */
async function navigateHome(page: Page): Promise<void> {
	await page.getByRole("link", { name: "TourDoum" }).first().click();
	await expect(page).toHaveURL("/");
}

test.describe("알림 — 트리거 + 읽음 처리 (qa #28)", () => {
	// 4 worker 병렬 시 signup 폼 동시 제출 → BE/FE race로 signupAndLogin 산발 실패.
	// 본 spec 은 serial 단일 worker 로 강제(시나리오 자체가 4건이라 시간 영향 작음).
	test.describe.configure({ mode: "serial" });
	test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

	test("Scenario A: 비로그인 /notifications → /login 리다이렉트", async ({
		page,
	}) => {
		await page.goto("/notifications");
		await expect(page).toHaveURL(/\/login/);
	});

	test("Scenario B: 예약 confirm 트리거 → /notifications 카드 노출 + 미읽음", async ({
		page,
	}) => {
		const { email, password } = await signupAndLogin(page, "qanotif");
		const accessToken = await loginApi(page, email, password);
		const accommodationId = await getFirstAccommodationId(page);

		// 신규 계정 → /notifications 진입 시 빈 상태
		await navigateToNotifications(page);
		await expect(page.getByText("새로운 알림이 없습니다.")).toBeVisible({
			timeout: 5000,
		});
		await page.screenshot({
			path: `${SCREENSHOT_DIR}/notifications-empty.png`,
			fullPage: true,
		});

		// home 으로 복귀 후 트리거 → 다시 /notifications 진입(onMounted refetch).
		await navigateHome(page);
		await triggerReservation(page, accessToken, accommodationId, 1);
		await navigateToNotifications(page);

		// 알림 카드 노출
		await expect(page.getByText("예약이 확정되었습니다").first()).toBeVisible({
			timeout: 5000,
		});
		await expect(page.getByText("새로운 알림이 없습니다.")).toHaveCount(0);

		// "전체 읽음" 버튼 노출 (unread > 0 v-if)
		await expect(page.getByRole("button", { name: /전체 읽음/ })).toBeVisible();

		// 미읽음 표식 (aria-label="미읽음")
		await expect(page.locator('[aria-label="미읽음"]').first()).toBeVisible();

		await page.screenshot({
			path: `${SCREENSHOT_DIR}/notifications-list-unread.png`,
			fullPage: true,
		});
	});

	test("Scenario C: 단건 읽음 — UI 카드 클릭 → unread count 감소 (FE-2 회귀)", async ({
		page,
	}) => {
		const { email, password } = await signupAndLogin(page, "qanotif");
		const accessToken = await loginApi(page, email, password);
		const accommodationId = await getFirstAccommodationId(page);

		// 알림 2건 트리거 (서로 다른 일자)
		await triggerReservation(page, accessToken, accommodationId, 5);
		await triggerReservation(page, accessToken, accommodationId, 10);

		await navigateToNotifications(page);

		// 미읽음 점 = 2개
		await expect(page.locator('[aria-label="미읽음"]')).toHaveCount(2, {
			timeout: 5000,
		});

		// BE unread count = 2 보장
		const beforeR = await page.request.get(
			`${BACKEND_URL}/api/notifications/unread-count`,
			{
				headers: { Authorization: `Bearer ${accessToken}` },
			},
		);
		expect(beforeR.ok()).toBeTruthy();
		const before = (await beforeR.json()).count as number;
		expect(before).toBeGreaterThanOrEqual(2);

		// FE-2 (CSRF interceptor) 머지 후: store.markAsRead UI 클릭이 정상 200.
		// 본 회차에 BE-direct → UI click 으로 회귀.
		// 알림 항목 컨테이너(li)의 첫 클릭 가능 영역. n.link("/reservations/me")가 RouterLink 으로
		// 적용되므로 클릭 시 SPA navigation 발생. POST /{id}/read 응답만 가로채 검증한다.
		const firstCard = page
			.locator("ul > li")
			.first()
			.locator('[class*="block focus-visible"]');
		const markPromise = page.waitForResponse(
			(r) =>
				r.url().includes("/api/notifications/") &&
				r.url().endsWith("/read") &&
				!r.url().endsWith("/read-all") &&
				r.request().method() === "POST",
			{ timeout: 5000 },
		);
		await firstCard.click();
		const markResp = await markPromise;
		expect(markResp.ok()).toBeTruthy();

		// 라우트 이동했을 가능성 — /notifications 로 복귀해 최신 목록 검증
		await navigateHome(page);
		await navigateToNotifications(page);
		await expect(page.locator('[aria-label="미읽음"]')).toHaveCount(1, {
			timeout: 5000,
		});

		// BE unread count 1 감소 검증
		const afterR = await page.request.get(
			`${BACKEND_URL}/api/notifications/unread-count`,
			{
				headers: { Authorization: `Bearer ${accessToken}` },
			},
		);
		expect(afterR.ok()).toBeTruthy();
		const after = (await afterR.json()).count as number;
		expect(after).toBe(before - 1);

		await page.screenshot({
			path: `${SCREENSHOT_DIR}/notifications-after-read.png`,
			fullPage: true,
		});
	});

	test("Scenario D: 전체 읽음 — UI 버튼 클릭 (FE-2 회귀)", async ({ page }) => {
		const { email, password } = await signupAndLogin(page, "qanotif");
		const accessToken = await loginApi(page, email, password);
		const accommodationId = await getFirstAccommodationId(page);

		await triggerReservation(page, accessToken, accommodationId, 15);
		await triggerReservation(page, accessToken, accommodationId, 20);

		await navigateToNotifications(page);

		const allReadBtn = page.getByRole("button", { name: /전체 읽음/ });
		await expect(allReadBtn).toBeVisible({ timeout: 5000 });

		// FE-2 (CSRF interceptor) 정상 동작 → UI click 회귀.
		const allReadPromise = page.waitForResponse(
			(r) =>
				r.url().endsWith("/api/notifications/read-all") &&
				r.request().method() === "POST",
			{ timeout: 5000 },
		);
		await allReadBtn.click();
		const resp = await allReadPromise;
		expect(resp.ok()).toBeTruthy();

		// 미읽음 점 모두 사라짐 (낙관적 업데이트 → 즉시 반영)
		await expect(page.locator('[aria-label="미읽음"]')).toHaveCount(0, {
			timeout: 5000,
		});
		// "전체 읽음" 버튼도 사라짐 (v-if unreadCount > 0)
		await expect(page.getByRole("button", { name: /전체 읽음/ })).toHaveCount(
			0,
		);

		// BE unread count 0
		const r = await page.request.get(
			`${BACKEND_URL}/api/notifications/unread-count`,
			{
				headers: { Authorization: `Bearer ${accessToken}` },
			},
		);
		expect(r.ok()).toBeTruthy();
		const count = (await r.json()).count as number;
		expect(count).toBe(0);

		await page.screenshot({
			path: `${SCREENSHOT_DIR}/notifications-all-read.png`,
			fullPage: true,
		});
	});
});
