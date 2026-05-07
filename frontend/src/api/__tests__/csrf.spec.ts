/**
 * csrf.spec.ts — CSRF interceptor 검증 (ADR-0011 FE-2, fe #65)
 *
 * BE `CookieCsrfTokenRepository.withHttpOnlyFalse()` 매 응답 XSRF-TOKEN cookie materialize.
 * SPA 는 mutation 시 cookie 값을 X-XSRF-TOKEN 헤더로 echo (double-submit).
 *
 * 검증 포인트:
 * - mutation(POST/PATCH/DELETE) → 헤더 부착
 * - GET/HEAD → 헤더 미부착 (BE 면제)
 * - cookie 부재 → 헤더 미부착
 * - URL-encoded cookie value → decodeURIComponent
 * - direct fetch path (reservations.confirm) 도 동일 정책
 */
import { del, get, patch, post } from "@/api/client";
import { isMutation, readXsrfToken } from "@/api/csrf";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import {
	afterAll,
	afterEach,
	beforeAll,
	beforeEach,
	describe,
	expect,
	it,
} from "vitest";

const TOKEN_VALUE = "csrf-token-uuid-1234";

let lastCsrfHeader: string | null = null;
let lastMethod: string | null = null;

const server = setupServer(
	http.all("http://localhost:8080/api/probe", ({ request }) => {
		lastCsrfHeader = request.headers.get("X-XSRF-TOKEN");
		lastMethod = request.method;
		return HttpResponse.json({ ok: true });
	}),
);

beforeAll(() => server.listen());
afterEach(() => {
	server.resetHandlers();
	lastCsrfHeader = null;
	lastMethod = null;
	// jsdom 의 모든 cookie 만료시켜 비움.
	for (const c of document.cookie.split("; ")) {
		const name = c.split("=")[0];
		if (name)
			document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
	}
});
afterAll(() => server.close());

describe("csrf.isMutation", () => {
	it("POST/PUT/PATCH/DELETE 는 mutation", () => {
		expect(isMutation("POST")).toBe(true);
		expect(isMutation("put")).toBe(true);
		expect(isMutation("PATCH")).toBe(true);
		expect(isMutation("DELETE")).toBe(true);
	});
	it("GET/HEAD/OPTIONS 는 면제", () => {
		expect(isMutation("GET")).toBe(false);
		expect(isMutation("HEAD")).toBe(false);
		expect(isMutation("OPTIONS")).toBe(false);
	});
});

describe("csrf.readXsrfToken", () => {
	it("XSRF-TOKEN cookie 값을 그대로 반환", () => {
		document.cookie = `XSRF-TOKEN=${TOKEN_VALUE}; path=/`;
		expect(readXsrfToken()).toBe(TOKEN_VALUE);
	});
	it("URL-encoded 값 decode", () => {
		document.cookie = `XSRF-TOKEN=${encodeURIComponent("a+b/c=d")}; path=/`;
		expect(readXsrfToken()).toBe("a+b/c=d");
	});
	it("cookie 미존재 시 null", () => {
		expect(readXsrfToken()).toBeNull();
	});
	it("다른 cookie 들 사이에서도 정확히 추출", () => {
		document.cookie = "OTHER=foo; path=/";
		document.cookie = `XSRF-TOKEN=${TOKEN_VALUE}; path=/`;
		document.cookie = "ANOTHER=bar; path=/";
		expect(readXsrfToken()).toBe(TOKEN_VALUE);
	});
});

describe("client.ts CSRF interceptor", () => {
	beforeEach(() => {
		document.cookie = `XSRF-TOKEN=${TOKEN_VALUE}; path=/`;
	});

	it("POST → X-XSRF-TOKEN 헤더 자동 부착", async () => {
		const res = await post<{ ok: boolean }>("/api/probe", { x: 1 });
		expect(res.error).toBeNull();
		expect(lastMethod).toBe("POST");
		expect(lastCsrfHeader).toBe(TOKEN_VALUE);
	});

	it("PATCH → 헤더 부착", async () => {
		await patch<{ ok: boolean }>("/api/probe", { x: 1 });
		expect(lastMethod).toBe("PATCH");
		expect(lastCsrfHeader).toBe(TOKEN_VALUE);
	});

	it("DELETE → 헤더 부착", async () => {
		await del("/api/probe");
		expect(lastMethod).toBe("DELETE");
		expect(lastCsrfHeader).toBe(TOKEN_VALUE);
	});

	it("GET → 헤더 미부착 (BE 면제)", async () => {
		await get<{ ok: boolean }>("/api/probe");
		expect(lastMethod).toBe("GET");
		expect(lastCsrfHeader).toBeNull();
	});

	it("cookie 미존재 + POST → 헤더 미부착 (BE 가 403 결정)", async () => {
		document.cookie =
			"XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
		await post<{ ok: boolean }>("/api/probe", { x: 1 });
		expect(lastCsrfHeader).toBeNull();
	});

	it("호출자가 명시적으로 X-XSRF-TOKEN 을 넘기면 그대로 사용 (덮어쓰기 방지)", async () => {
		await post<{ ok: boolean }>(
			"/api/probe",
			{ x: 1 },
			{ headers: { "X-XSRF-TOKEN": "explicit-override" } },
		);
		expect(lastCsrfHeader).toBe("explicit-override");
	});
});
