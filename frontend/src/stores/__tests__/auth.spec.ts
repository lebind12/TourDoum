/**
 * auth.spec.ts — JWT Bearer 호환층 검증 (ADR-0011 FE-1, fe #62)
 *
 * BE #61 응답 contract:
 * POST /api/auth/login 200 → {accessToken, expiresInSeconds, tokenType, user}
 * POST /api/auth/logout 204 (BE-2 머지 전 stub)
 * GET  /api/me 200 → User (Authorization: Bearer 검증)
 */
import { clearAccessToken, getAccessToken } from "@/api/auth-token";
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
} from "vitest";
import { useAuthStore } from "../auth";

const FAKE_TOKEN = "test.jwt.token";
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
	user: USER,
};

let lastAuthHeader: string | null = null;

const server = setupServer(
	http.post("http://localhost:8080/api/auth/login", () => {
		return HttpResponse.json(LOGIN_SUCCESS_RESPONSE);
	}),
	http.post("http://localhost:8080/api/auth/logout", () => {
		return new HttpResponse(null, { status: 204 });
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
	lastAuthHeader = null;
});
afterAll(() => server.close());

describe("useAuthStore — login()", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		clearAccessToken();
	});

	it("로그인 성공 시 accessToken/user 양쪽 모두 박제된다", async () => {
		const store = useAuthStore();

		const result = await store.login({
			email: "user@example.com",
			password: "pass123",
		});

		expect(result).toBe(true);
		expect(store.currentUser?.id).toBe(42);
		expect(store.currentUser?.role).toBe("ROLE_USER");
		expect(store.accessToken).toBe(FAKE_TOKEN);
		// 모듈 스코프 토큰도 동기화됨 → 후속 요청 Bearer 자동 부착의 진실 소스.
		expect(getAccessToken()).toBe(FAKE_TOKEN);
		// 만료 시각은 미래 (epoch ms).
		expect(store.expiresAt).toBeGreaterThan(Date.now());
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
		expect(getAccessToken()).toBeNull();
		expect(store.error).toBeTruthy();
	});

	it("응답 형식이 contract와 다르면(빈 응답 등) silent fallback 없이 실패한다", async () => {
		server.use(
			http.post("http://localhost:8080/api/auth/login", () => {
				return new HttpResponse(null, { status: 204 });
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
		expect(getAccessToken()).toBeNull();
	});

	it("토큰이 없는 상태에서 fetchMe()는 API 호출 없이 즉시 false 반환", async () => {
		const store = useAuthStore();
		const ok = await store.fetchMe();
		expect(ok).toBe(false);
		expect(lastAuthHeader).toBeNull();
		expect(store.currentUser).toBeNull();
	});
});

describe("useAuthStore — logout()/clearSession()", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		clearAccessToken();
	});

	it("logout 호출 시 accessToken/user/모듈 스코프가 모두 비워진다", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });
		expect(store.accessToken).toBe(FAKE_TOKEN);

		await store.logout();
		expect(store.accessToken).toBeNull();
		expect(store.currentUser).toBeNull();
		expect(getAccessToken()).toBeNull();
	});

	it("clearSession() 단독 호출 시에도 동일하게 모든 상태가 비워진다", async () => {
		const store = useAuthStore();
		await store.login({ email: "user@example.com", password: "pass123" });

		store.clearSession();
		expect(store.accessToken).toBeNull();
		expect(getAccessToken()).toBeNull();
		expect(store.currentUser).toBeNull();
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

	it("회원가입 성공 시 자동 로그인으로 currentUser/accessToken이 설정된다", async () => {
		const store = useAuthStore();
		const result = await store.signup({
			email: "user@example.com",
			password: "pass123",
			nickname: "여행자",
		});

		expect(result).toBe(true);
		expect(store.currentUser?.role).toBe("ROLE_USER");
		expect(store.accessToken).toBe(FAKE_TOKEN);
	});
});
