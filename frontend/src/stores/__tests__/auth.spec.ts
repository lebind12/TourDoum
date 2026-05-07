/**
 * auth.spec.ts — JWT Bearer 호환층 + refresh interceptor (ADR-0011 FE-1 보강, fe #63)
 *
 * BE-2 응답 contract:
 * POST /api/auth/login    200 → {accessToken, expiresInSeconds, tokenType, refreshToken, refreshExpiresInSeconds, user}
 * POST /api/auth/refresh  body {refreshToken} → 200 LoginResponse / 401
 * POST /api/auth/logout   Bearer + body {refreshToken?} → 204
 * GET  /api/me            200 → User (Authorization: Bearer 검증)
 */
import {
	_resetRefreshState,
	clearAccessToken,
	getAccessToken,
	registerRefreshHandler,
	registerUnauthorizedHandler,
} from "@/api/auth-token";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { createPinia, setActivePinia } from "pinia";
import {
	afterAll,
	afterEach,
	beforeAll,
	beforeEach,
	describe,
	expect,
	it,
	vi,
} from "vitest";
import { useAuthStore } from "../auth";

const FAKE_TOKEN = "test.jwt.token";
const FAKE_REFRESH = "refresh.jwt.token";
const NEW_TOKEN = "new.jwt.token";
const NEW_REFRESH = "new.refresh.token";
const USER = {
	id: 42,
	email: "user@example.com",
	nickname: "여행자",
	role: "ROLE_USER",
};
const LOGIN_SUCCESS_RESPONSE = {
	accessToken: FAKE_TOKEN,
	expiresInSeconds: 900,
	tokenType: "Bearer",
	refreshToken: FAKE_REFRESH,
	refreshExpiresInSeconds: 1209600,
	user: USER,
};
const REFRESH_SUCCESS_RESPONSE = {
	accessToken: NEW_TOKEN,
	expiresInSeconds: 900,
	tokenType: "Bearer",
	refreshToken: NEW_REFRESH,
	refreshExpiresInSeconds: 1209600,
	user: USER,
};

let lastAuthHeader: string | null = null;
let lastLogoutBody: unknown = null;
let lastRefreshBody: unknown = null;
let refreshCallCount = 0;

const server = setupServer(
	http.post("http://localhost:8080/api/auth/login", () => {
		return HttpResponse.json(LOGIN_SUCCESS_RESPONSE);
	}),
	http.post("http://localhost:8080/api/auth/logout", async ({ request }) => {
		lastAuthHeader = request.headers.get("Authorization");
		try {
			lastLogoutBody = await request.json();
		} catch {
			lastLogoutBody = null;
		}
		return new HttpResponse(null, { status: 204 });
	}),
	http.post("http://localhost:8080/api/auth/refresh", async ({ request }) => {
		refreshCallCount += 1;
		try {
			lastRefreshBody = await request.json();
		} catch {
			lastRefreshBody = null;
		}
		return HttpResponse.json(REFRESH_SUCCESS_RESPONSE);
	}),
	http.get("http://localhost:8080/api/me", ({ request }) => {
		lastAuthHeader = request.headers.get("Authorization");
		if (!lastAuthHeader) {
			return new HttpResponse(null, { status: 401 });
		}
		return HttpResponse.json(USER);
	}),
);

beforeAll(() => server.listen());
afterEach(() => {
	server.resetHandlers();
	clearAccessToken();
	_resetRefreshState();
	lastAuthHeader = null;
	lastLogoutBody = null;
	lastRefreshBody = null;
	refreshCallCount = 0;
});
afterAll(() => server.close());

describe("useAuthStore — login()", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		clearAccessToken();
	});

	it("로그인 성공 시 access/refresh 토큰과 user 가 모두 박제된다", async () => {
		const store = useAuthStore();

		const result = await store.login({
			email: "user@example.com",
			password: "pass123",
		});

		expect(result).toBe(true);
		expect(store.currentUser?.id).toBe(42);
		expect(store.accessToken).toBe(FAKE_TOKEN);
		expect(store.refreshToken).toBe(FAKE_REFRESH);
		expect(getAccessToken()).toBe(FAKE_TOKEN);
		expect(store.expiresAt).toBeGreaterThan(Date.now());
		expect(store.refreshExpiresAt).toBeGreaterThan(Date.now());
	});

	it("로그인 성공 후 fetchMe()는 Authorization: Bearer 헤더를 부착한다", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });

		const ok = await store.fetchMe();
		expect(ok).toBe(true);
		expect(lastAuthHeader).toBe(`Bearer ${FAKE_TOKEN}`);
	});

	it("4xx 오류 시 currentUser/accessToken은 null, error 세트", async () => {
		server.use(
			http.post("http://localhost:8080/api/auth/login", () => {
				return HttpResponse.json(
					{ message: "이메일 또는 비밀번호가 올바르지 않습니다." },
					{ status: 401 },
				);
			}),
		);

		const store = useAuthStore();
		const result = await store.login({
			email: "wrong@example.com",
			password: "wrong",
		});

		expect(result).toBe(false);
		expect(store.currentUser).toBeNull();
		expect(store.accessToken).toBeNull();
		expect(store.refreshToken).toBeNull();
		expect(getAccessToken()).toBeNull();
		expect(store.error).toBeTruthy();
	});

	it("응답에 refreshToken이 빠지면 silent fallback 없이 실패한다", async () => {
		server.use(
			http.post("http://localhost:8080/api/auth/login", () => {
				return HttpResponse.json({
					accessToken: FAKE_TOKEN,
					expiresInSeconds: 900,
					tokenType: "Bearer",
					user: USER,
				});
			}),
		);

		const store = useAuthStore();
		const result = await store.login({
			email: "user@example.com",
			password: "pass123",
		});

		expect(result).toBe(false);
		expect(store.error).toBe("로그인 응답 형식이 올바르지 않습니다.");
		expect(store.accessToken).toBeNull();
		expect(store.refreshToken).toBeNull();
	});

	it("토큰이 없는 상태에서 fetchMe()는 API 호출 없이 즉시 false 반환", async () => {
		const store = useAuthStore();
		const ok = await store.fetchMe();
		expect(ok).toBe(false);
		expect(lastAuthHeader).toBeNull();
		expect(store.currentUser).toBeNull();
	});
});

describe("useAuthStore — logout()", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		clearAccessToken();
	});

	it("logout 호출 시 Bearer + body {refreshToken} 동봉, 모든 상태 클리어", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });

		await store.logout();

		expect(lastAuthHeader).toBe(`Bearer ${FAKE_TOKEN}`);
		expect(lastLogoutBody).toEqual({ refreshToken: FAKE_REFRESH });
		expect(store.accessToken).toBeNull();
		expect(store.refreshToken).toBeNull();
		expect(store.currentUser).toBeNull();
		expect(getAccessToken()).toBeNull();
	});
});

describe("useAuthStore — refresh interceptor flow", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		clearAccessToken();
		_resetRefreshState();
		// main.ts 와 동일하게 refresh 핸들러 등록.
		registerRefreshHandler(() => useAuthStore().refresh());
	});

	it("401 만료 응답 → refresh 자동 호출 → 새 access 로 원 요청 1회 재시도", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });

		// /api/me 가 한 번은 401, 두 번째 호출(새 토큰)에는 200.
		let meCallCount = 0;
		server.use(
			http.get("http://localhost:8080/api/me", ({ request }) => {
				meCallCount += 1;
				const auth = request.headers.get("Authorization");
				if (meCallCount === 1) {
					return new HttpResponse(null, { status: 401 });
				}
				if (auth === `Bearer ${NEW_TOKEN}`) {
					return HttpResponse.json(USER);
				}
				return new HttpResponse(null, { status: 401 });
			}),
		);

		const ok = await store.fetchMe();

		expect(ok).toBe(true);
		expect(meCallCount).toBe(2);
		expect(refreshCallCount).toBe(1);
		expect(lastRefreshBody).toEqual({ refreshToken: FAKE_REFRESH });
		expect(store.accessToken).toBe(NEW_TOKEN);
		expect(store.refreshToken).toBe(NEW_REFRESH);
	});

	it("동시 401 다발 시 refresh 는 단 1회만 호출된다 (in-flight 단일화)", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });

		const meTokens: (string | null)[] = [];
		server.use(
			http.get("http://localhost:8080/api/me", ({ request }) => {
				const auth = request.headers.get("Authorization");
				meTokens.push(auth);
				if (auth === `Bearer ${NEW_TOKEN}`) {
					return HttpResponse.json(USER);
				}
				return new HttpResponse(null, { status: 401 });
			}),
		);

		const [a, b, c] = await Promise.all([
			store.fetchMe(),
			store.fetchMe(),
			store.fetchMe(),
		]);

		expect(a && b && c).toBe(true);
		expect(refreshCallCount).toBe(1);
		// 3건이 만료된 토큰으로 1회씩 시도 + 새 토큰으로 1회씩 재시도 → 6건.
		expect(meTokens.length).toBe(6);
	});

	it("refresh 자체가 401 → clearSession + unauthorized 핸들러 호출", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });

		const onUnauthorized = vi.fn();
		registerUnauthorizedHandler(onUnauthorized);

		server.use(
			http.post("http://localhost:8080/api/auth/refresh", () => {
				return new HttpResponse(null, { status: 401 });
			}),
			http.get("http://localhost:8080/api/me", () => {
				return new HttpResponse(null, { status: 401 });
			}),
		);

		const ok = await store.fetchMe();

		expect(ok).toBe(false);
		expect(store.accessToken).toBeNull();
		expect(store.refreshToken).toBeNull();
		expect(onUnauthorized).toHaveBeenCalled();
	});

	it("refreshToken 이 없으면 refresh()는 즉시 false 반환 (네트워크 미호출)", async () => {
		const store = useAuthStore();
		// 로그인 없이 refresh 직호출.
		const ok = await store.refresh();
		expect(ok).toBe(false);
		expect(refreshCallCount).toBe(0);
	});
});

describe("useAuthStore — signup() → login() 연쇄", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		clearAccessToken();
		server.use(
			http.post("http://localhost:8080/api/members/signup", () => {
				return HttpResponse.json(USER);
			}),
		);
	});

	it("회원가입 성공 시 자동 로그인으로 access/refresh 토큰이 설정된다", async () => {
		const store = useAuthStore();
		const result = await store.signup({
			email: "user@example.com",
			password: "pass123",
			nickname: "여행자",
		});

		expect(result).toBe(true);
		expect(store.currentUser?.role).toBe("ROLE_USER");
		expect(store.accessToken).toBe(FAKE_TOKEN);
		expect(store.refreshToken).toBe(FAKE_REFRESH);
	});
});
