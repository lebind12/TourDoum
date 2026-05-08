/**
 * e2e — Chat 폴링 (qa #30)
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행. BE 30080, FE 30174 (.env.agent).
 *
 *   set -a; . .env.agent; set +a
 *   E2E_BACKEND=1 npx playwright test e2e/chat-polling.spec.ts
 *
 * Scenario A: DM 폴링 — A signup → B signup → A가 DM open → A 채널 진입 →
 *   B가 API로 메시지 전송 → A의 UI에 setInterval 2s 폴링으로 새 메시지 노출.
 * Scenario B: PUBLIC 채널 폴링 — `test.skip`. ADR-0012 BE-3 dev-only seed runner 미머지.
 *
 * [BE 폴링 ADR-0012 v2]
 * - GET /api/chat/channels/{id}/messages?afterCursor=<base64url> — forward cursor.
 * - 한시 호환 ?sinceId=<id>. FE chat store는 현재 sinceId 사용(BE-2 V17/Cursor 마이그레이션 후
 *   afterCursor로 전환 예정).
 * - POLL_INTERVAL_MS = 2_000 (chat.ts).
 *
 * [BE-3 CSRF]
 * - mutation: X-XSRF-TOKEN + Cookie 명시 (qa #28 패턴 재사용).
 * - signup/login 면제. /api/chat/dm, /api/chat/channels/{id}/messages POST는 보호 대상.
 *
 * [JWT module-closure 회피]
 * - SPA navigation: AppShell의 "채팅" RouterLink → /chat → DM 탭 → 채널 카드 클릭 → /chat/{id}.
 * - page.goto 금지(qa #28 §2 처방).
 */
import {
	type APIRequestContext,
	type Page,
	expect,
	test,
} from "@playwright/test";
import { signupAndLogin } from "./_helpers/auth";

const RUN = process.env.E2E_BACKEND === "1";
const BACKEND_URL = process.env.VITE_API_BASE_URL ?? "http://localhost:30080";
const SCREENSHOT_DIR = "e2e/screenshots";

interface UserCreds {
	id: number;
	email: string;
	password: string;
	nickname: string;
	accessToken: string;
}

/**
 * BE 직접 회원가입 + 로그인 — page.request 사용. UI 진입 없이 두번째 사용자(B) 생성용.
 */
async function registerUserApi(
	request: APIRequestContext,
	prefix: string,
): Promise<UserCreds> {
	const ts = Date.now();
	const rand = Math.floor(Math.random() * 10000);
	const email = `${prefix}-${ts}-${rand}@example.com`;
	// BE-4 PasswordPolicyValidator 회피 (12+자, blocklist/유사값 회피).
	const password = "E2eTestSecure!9x";
	const nickname = `${prefix}${ts}${rand}`.slice(0, 20);

	const signupResp = await request.post(`${BACKEND_URL}/api/members/signup`, {
		data: { email, password, nickname },
	});
	expect(signupResp.ok()).toBeTruthy();
	const signupBody = await signupResp.json();
	const id = signupBody.id as number;

	const loginResp = await request.post(`${BACKEND_URL}/api/auth/login`, {
		data: { email, password },
	});
	expect(loginResp.ok()).toBeTruthy();
	const loginBody = await loginResp.json();
	const accessToken = loginBody.accessToken as string;

	return { id, email, password, nickname, accessToken };
}

/** raw accessToken 회수 (qa #28 패턴). */
async function loginApi(
	request: APIRequestContext,
	email: string,
	password: string,
): Promise<string> {
	const r = await request.post(`${BACKEND_URL}/api/auth/login`, {
		data: { email, password },
	});
	expect(r.ok()).toBeTruthy();
	const body = await r.json();
	return body.accessToken as string;
}

/**
 * XSRF-TOKEN 회수 — Set-Cookie 응답 헤더에서 우선 추출, 없으면 context jar에서 fallback.
 * BE는 기존 쿠키를 가진 클라이언트에 대해 매 응답에 Set-Cookie를 갱신하지 않을 수 있음(qa #28 패턴).
 */
async function getCsrfToken(page: Page): Promise<string> {
	const r = await page.request.get(`${BACKEND_URL}/api/health`);
	const setCookie = r.headers()["set-cookie"];
	if (setCookie) {
		const m = setCookie.match(/XSRF-TOKEN=([^;\s,]+)/);
		if (m?.[1] && m[1].length > 1) return m[1];
	}
	const cookies = await page.context().cookies(BACKEND_URL);
	const xsrf = cookies.find((c) => c.name === "XSRF-TOKEN" && c.value);
	if (!xsrf) throw new Error("XSRF-TOKEN cookie 미설정 — BE-3 csrf 설정 확인");
	return xsrf.value;
}

/** A가 B와 DM 채널 open. 멱등 — 기존 채널 있으면 재사용. */
async function openDm(
	page: Page,
	accessToken: string,
	otherMemberId: number,
): Promise<{ id: string; name: string; type: string }> {
	const csrf = await getCsrfToken(page);
	const r = await page.request.post(`${BACKEND_URL}/api/chat/dm`, {
		headers: {
			Authorization: `Bearer ${accessToken}`,
			"X-XSRF-TOKEN": csrf,
			Cookie: `XSRF-TOKEN=${csrf}`,
		},
		data: { otherMemberId },
	});
	expect(r.ok()).toBeTruthy();
	const body = await r.json();
	return { id: String(body.id), name: body.name, type: body.type };
}

/** API direct 메시지 전송 — 폴링 트리거용. */
async function sendChatMessage(
	page: Page,
	accessToken: string,
	channelId: string,
	content: string,
): Promise<void> {
	const csrf = await getCsrfToken(page);
	const r = await page.request.post(
		`${BACKEND_URL}/api/chat/channels/${channelId}/messages`,
		{
			headers: {
				Authorization: `Bearer ${accessToken}`,
				"X-XSRF-TOKEN": csrf,
				Cookie: `XSRF-TOKEN=${csrf}`,
			},
			data: { content },
		},
	);
	expect(r.ok()).toBeTruthy();
}

/** SPA nav: 채팅 → DM 탭 → 첫 DM 채널 → /chat/{id}. */
async function navigateToDmChannel(
	page: Page,
	channelName: string,
): Promise<void> {
	// AppShell 의 "채팅" 메뉴(데스크톱 + 모바일) → .first()
	await page.getByRole("link", { name: "채팅", exact: true }).first().click();
	await expect(page).toHaveURL(/\/chat$/);

	// DM 탭 활성화
	await page.getByRole("tab", { name: /다이렉트 메시지/ }).click();

	// 채널 카드 클릭 (h2 채널 이름 매칭)
	await page.getByRole("heading", { name: channelName }).first().click();
	await expect(page).toHaveURL(/\/chat\/\d+$/);
}

test.describe("채팅 폴링 (qa #30)", () => {
	// qa #28 §6 — parallel signup race 회피
	test.describe.configure({ mode: "serial" });
	test.skip(!RUN, "E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.");

	// BLOCKER: FE chat store(stores/chat.ts) BE-1 contract 미적응.
	// `fetchMessages`가 응답을 `ChatMessageApiResponse[]` 로 가정하나 BE는
	// `ChatMessagePage{items, nextCursor, appliedLimit, hasMore}` 반환.
	// `(result.data ?? []).map(...)` 호출 시 TypeError → 폴링 무동작(메시지 미노출).
	// ADR-0012 FE-1 (ChatView keyset state + items 매핑) 머지 후 `test()` 로 복원.
	// BE 라운드트립은 curl 로 별도 검증 (handoff §"FE 차단" 참조).
	test("Scenario A (DM): B가 API로 보낸 메시지가 A의 UI에 폴링으로 노출", async ({
		page,
	}) => {
		// A: UI signup + login (브라우저 module-closure 박제)
		const A = await signupAndLogin(page, "qachata");
		const aToken = await loginApi(page.request, A.email, A.password);

		// B: API direct signup + login
		const B = await registerUserApi(page.request, "qachatb");

		// A → B와 DM open
		const dm = await openDm(page, aToken, B.id);

		// SPA nav: 채팅 → DM 탭 → 채널 진입
		await navigateToDmChannel(page, dm.name);

		// 활성 채널 헤더에 채널 이름 노출 확인
		await expect(
			page.locator("header h1").filter({ hasText: dm.name }),
		).toBeVisible({
			timeout: 5000,
		});

		// 초기 상태: 메시지 0건 (DM 신규)
		await page.screenshot({
			path: `${SCREENSHOT_DIR}/chat-polling-dm-initial.png`,
			fullPage: false,
		});

		// 폴링 트리거: B가 API로 메시지 전송
		const probeText = `e2e poll probe ${Date.now()}`;
		await sendChatMessage(page, B.accessToken, dm.id, probeText);

		// POLL_INTERVAL_MS = 2_000 → 안전 마진 8s 내에 UI 노출 기대
		await expect(page.getByText(probeText)).toBeVisible({ timeout: 8000 });

		await page.screenshot({
			path: `${SCREENSHOT_DIR}/chat-polling-dm-after-poll.png`,
			fullPage: false,
		});

		// 추가 메시지 — 폴링 증분 검증
		const probeText2 = `e2e poll probe second ${Date.now()}`;
		await sendChatMessage(page, B.accessToken, dm.id, probeText2);
		await expect(page.getByText(probeText2)).toBeVisible({ timeout: 8000 });

		// 첫 메시지가 사라지지 않고 누적됨
		await expect(page.getByText(probeText)).toBeVisible();
	});

	test("Scenario B (PUBLIC): A가 PUBLIC 채널 진입 → B가 API로 보낸 메시지가 폴링으로 노출", async ({
		page,
	}) => {
		// 전제: BE가 dev,chat-seed --scenario=public로 1회 seed 후 dev 단독 재기동된 상태.
		// signup 시 ChatPublicAutoJoinListener(@Profile("dev"))가 신규 회원을 seed-public-1 채널에
		// 자동 join → /api/chat/channels 응답에 PUBLIC 채널 노출.
		const A = await signupAndLogin(page, "qachatpa");
		const aToken = await loginApi(page.request, A.email, A.password);

		// B: API direct signup + login (autojoin listener가 동작 → seed-public-1에 join).
		const B = await registerUserApi(page.request, "qachatpb");

		// A의 채널 목록에서 PUBLIC seed 채널 회수.
		const channelsResp = await page.request.get(
			`${BACKEND_URL}/api/chat/channels`,
			{ headers: { Authorization: `Bearer ${aToken}` } },
		);
		expect(channelsResp.ok()).toBeTruthy();
		const channels = (await channelsResp.json()) as Array<{
			id: number;
			name: string;
			type: string;
		}>;
		const pub = channels.find((c) => c.type === "PUBLIC");
		expect(
			pub,
			"seed PUBLIC 채널 미존재 — chat-seed 1단계 부팅 필요",
		).toBeTruthy();
		const pubChannel = pub as { id: number; name: string; type: string };

		// SPA nav: 채팅 → (PUBLIC 탭 또는 첫 채널) → /chat/{id}.
		await page.getByRole("link", { name: "채팅", exact: true }).first().click();
		await expect(page).toHaveURL(/\/chat$/);
		// 채널 카드 클릭 — heading 매칭 (탭 default가 PUBLIC이라면 직접 click).
		await page.getByRole("heading", { name: pubChannel.name }).first().click();
		await expect(page).toHaveURL(/\/chat\/\d+$/);
		await expect(
			page.locator("header h1").filter({ hasText: pubChannel.name }),
		).toBeVisible({ timeout: 5000 });

		await page.screenshot({
			path: `${SCREENSHOT_DIR}/chat-polling-public-initial.png`,
			fullPage: false,
		});

		// 폴링 트리거: B가 API로 PUBLIC 채널에 메시지 전송.
		const probeText = `e2e public probe ${Date.now()}`;
		await sendChatMessage(
			page,
			B.accessToken,
			String(pubChannel.id),
			probeText,
		);

		// POLL_INTERVAL_MS = 2_000 → 8s 마진.
		await expect(page.getByText(probeText)).toBeVisible({ timeout: 8000 });

		await page.screenshot({
			path: `${SCREENSHOT_DIR}/chat-polling-public-after-poll.png`,
			fullPage: false,
		});

		// 두 번째 메시지 — dedupe append 회귀.
		const probeText2 = `e2e public probe second ${Date.now()}`;
		await sendChatMessage(
			page,
			B.accessToken,
			String(pubChannel.id),
			probeText2,
		);
		await expect(page.getByText(probeText2)).toBeVisible({ timeout: 8000 });
		await expect(page.getByText(probeText)).toBeVisible();
	});
});
