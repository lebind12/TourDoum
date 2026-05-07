/**
 * fetch 래퍼 — JWT Bearer 호환층 + refresh interceptor + CSRF 자동 주입 (ADR-0011 FE-1/FE-2).
 *
 * - `auth-token.ts`의 `getAccessToken()`이 토큰을 반환하면 매 요청에
 *   `Authorization: Bearer <token>` 헤더를 자동 부착.
 * - 응답 status 401 시:
 *     1) `skipRefresh` 옵션이 true 가 아니고 refresh 핸들러가 등록돼 있으면
 *        `tryRefresh()` 로 단일 in-flight refresh 호출 → 성공 시 원 요청 1회 재시도.
 *     2) refresh 실패하거나 재시도 후에도 401 이면 `notifyUnauthorized()` 호출.
 * - `credentials: "include"` 로 httpOnly refresh cookie + XSRF-TOKEN cookie 자동 송수신.
 * - mutation(POST/PUT/PATCH/DELETE) 시 `X-XSRF-TOKEN` 헤더 자동 주입(double-submit).
 *   GET/HEAD/OPTIONS + login/signup 은 BE 측에서 CSRF 면제 — cookie 부재 시 헤더 생략.
 *
 * VITE_API_BASE_URL이 없으면 사용자 로컬 dev(8080) 폴백. agent worktree는 .env.agent로 30080 주입.
 */

import { getAccessToken, notifyUnauthorized, tryRefresh } from "./auth-token";
import { CSRF_HEADER_NAME, isMutation, readXsrfToken } from "./csrf";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface ApiResponse<T> {
	data: T | null;
	error: string | null;
}

export interface RequestOptions {
	/** /api/auth/refresh 자체 호출처럼 401 → refresh 재진입을 막아야 할 때 true. */
	skipRefresh?: boolean;
	/** 추가 헤더(예: Idempotency-Key). */
	headers?: HeadersInit;
}

/** 4xx/5xx 응답 본문에서 메시지를 추출한다. */
async function extractError(res: Response): Promise<string> {
	try {
		const body = await res.json();
		return body?.message ?? `HTTP ${res.status}`;
	} catch {
		return `HTTP ${res.status}`;
	}
}

/**
 * 인증 + CSRF 헤더를 구성한다. 호출자가 헤더를 추가로 넘기면 병합.
 * - mutation method 면 `X-XSRF-TOKEN` cookie 값을 echo (BE double-submit 검증).
 * - cookie 가 없으면 헤더 미부착 (login/signup 은 BE 면제, 나머지는 403 응답이 호출자에게 전달).
 */
function buildHeaders(
	method: string,
	extra?: HeadersInit,
	withJson = true,
): Headers {
	const headers = new Headers(extra);
	if (withJson && !headers.has("Content-Type")) {
		headers.set("Content-Type", "application/json");
	}
	const token = getAccessToken();
	if (token && !headers.has("Authorization")) {
		headers.set("Authorization", `Bearer ${token}`);
	}
	if (isMutation(method) && !headers.has(CSRF_HEADER_NAME)) {
		const xsrf = readXsrfToken();
		if (xsrf) headers.set(CSRF_HEADER_NAME, xsrf);
	}
	return headers;
}

interface RawRequestInit {
	method: string;
	body?: string;
	headers?: HeadersInit;
	withJson: boolean;
}

/**
 * 공통 fetch 호출. 401 시 1회 refresh + retry 까지 수행한다.
 * 본 함수는 ApiResponse 로 정규화하지 않고 Response 를 그대로 반환 — 상위 메서드가 본문 파싱.
 */
async function rawFetch(
	path: string,
	init: RawRequestInit,
	options?: RequestOptions,
): Promise<Response> {
	const buildInit = (): RequestInit => ({
		method: init.method,
		credentials: "include",
		headers: buildHeaders(
			init.method,
			init.headers ?? options?.headers,
			init.withJson,
		),
		body: init.body,
	});

	let res = await fetch(`${BASE_URL}${path}`, buildInit());

	if (res.status !== 401 || options?.skipRefresh === true) {
		return res;
	}

	// 401 → refresh 시도. 성공 시 새 access 로 1회만 재시도.
	const refreshed = await tryRefresh();
	if (!refreshed) {
		notifyUnauthorized();
		return res;
	}

	res = await fetch(`${BASE_URL}${path}`, buildInit());
	if (res.status === 401) {
		notifyUnauthorized();
	}
	return res;
}

async function toApiResponse<T>(res: Response): Promise<ApiResponse<T>> {
	if (!res.ok) {
		return { data: null, error: await extractError(res) };
	}
	const text = await res.text();
	const data: T = text ? (JSON.parse(text) as T) : (null as T);
	return { data, error: null };
}

export async function get<T>(
	path: string,
	options?: RequestOptions,
): Promise<ApiResponse<T>> {
	try {
		const res = await rawFetch(
			path,
			{ method: "GET", withJson: false, headers: options?.headers },
			options,
		);
		return toApiResponse<T>(res);
	} catch (err) {
		return {
			data: null,
			error: err instanceof Error ? err.message : "알 수 없는 오류",
		};
	}
}

export async function post<T>(
	path: string,
	body: unknown,
	options?: RequestOptions,
): Promise<ApiResponse<T>> {
	try {
		const res = await rawFetch(
			path,
			{
				method: "POST",
				withJson: true,
				headers: options?.headers,
				body: JSON.stringify(body),
			},
			options,
		);
		return toApiResponse<T>(res);
	} catch (err) {
		return {
			data: null,
			error: err instanceof Error ? err.message : "알 수 없는 오류",
		};
	}
}

export async function patch<T>(
	path: string,
	body: unknown,
	options?: RequestOptions,
): Promise<ApiResponse<T>> {
	try {
		const res = await rawFetch(
			path,
			{
				method: "PATCH",
				withJson: true,
				headers: options?.headers,
				body: JSON.stringify(body),
			},
			options,
		);
		return toApiResponse<T>(res);
	} catch (err) {
		return {
			data: null,
			error: err instanceof Error ? err.message : "알 수 없는 오류",
		};
	}
}

/** DELETE 요청. 204 No Content 정상 응답 처리. */
export async function del(
	path: string,
	options?: RequestOptions,
): Promise<ApiResponse<null>> {
	try {
		const res = await rawFetch(
			path,
			{ method: "DELETE", withJson: false, headers: options?.headers },
			options,
		);
		if (!res.ok) {
			return { data: null, error: await extractError(res) };
		}
		return { data: null, error: null };
	} catch (err) {
		return {
			data: null,
			error: err instanceof Error ? err.message : "알 수 없는 오류",
		};
	}
}
