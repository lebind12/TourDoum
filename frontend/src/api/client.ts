/**
 * fetch 래퍼 — JWT Bearer 호환층 (ADR-0011 FE-1).
 *
 * - `auth-token.ts`의 `getAccessToken()`이 토큰을 반환하면 매 요청에
 *   `Authorization: Bearer <token>` 헤더를 자동 부착.
 * - 응답 status 401 → `notifyUnauthorized()` 호출 (라우터/store는 main.ts에서 등록한 콜백이 처리).
 * - `credentials: "include"`는 BE-3(httpOnly refresh cookie) 도입 대비해 유지.
 *   현재 BE는 cookie 미발급이므로 송수신 영향 없음.
 *
 * VITE_API_BASE_URL이 없으면 사용자 로컬 dev(8080) 폴백. agent worktree는 .env.agent로 30080 주입.
 */

import { getAccessToken, notifyUnauthorized } from "./auth-token";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface ApiResponse<T> {
	data: T | null;
	error: string | null;
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

/** 인증 헤더를 구성한다. 호출자가 헤더를 추가로 넘기면 병합. */
function buildHeaders(extra?: HeadersInit): Headers {
	const headers = new Headers(extra);
	if (!headers.has("Content-Type")) {
		headers.set("Content-Type", "application/json");
	}
	const token = getAccessToken();
	if (token && !headers.has("Authorization")) {
		headers.set("Authorization", `Bearer ${token}`);
	}
	return headers;
}

/** 401 응답을 가로채 핸들러에 통지한 뒤 일반 error 반환. (호출자 흐름 유지) */
function handleUnauthorized(): void {
	notifyUnauthorized();
}

export async function get<T>(path: string): Promise<ApiResponse<T>> {
	try {
		const res = await fetch(`${BASE_URL}${path}`, {
			credentials: "include",
			headers: buildHeaders(),
		});
		if (res.status === 401) handleUnauthorized();
		if (!res.ok) {
			return { data: null, error: await extractError(res) };
		}
		const data: T = await res.json();
		return { data, error: null };
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
): Promise<ApiResponse<T>> {
	try {
		const res = await fetch(`${BASE_URL}${path}`, {
			method: "POST",
			headers: buildHeaders(),
			credentials: "include",
			body: JSON.stringify(body),
		});
		if (res.status === 401) handleUnauthorized();
		if (!res.ok) {
			return { data: null, error: await extractError(res) };
		}
		// 204 No Content 등 빈 응답 처리
		const text = await res.text();
		const data: T = text ? (JSON.parse(text) as T) : (null as T);
		return { data, error: null };
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
): Promise<ApiResponse<T>> {
	try {
		const res = await fetch(`${BASE_URL}${path}`, {
			method: "PATCH",
			headers: buildHeaders(),
			credentials: "include",
			body: JSON.stringify(body),
		});
		if (res.status === 401) handleUnauthorized();
		if (!res.ok) {
			return { data: null, error: await extractError(res) };
		}
		const text = await res.text();
		const data: T = text ? (JSON.parse(text) as T) : (null as T);
		return { data, error: null };
	} catch (err) {
		return {
			data: null,
			error: err instanceof Error ? err.message : "알 수 없는 오류",
		};
	}
}

/** DELETE 요청. 204 No Content 정상 응답 처리. */
export async function del(path: string): Promise<ApiResponse<null>> {
	try {
		const res = await fetch(`${BASE_URL}${path}`, {
			method: "DELETE",
			credentials: "include",
			headers: buildHeaders(),
		});
		if (res.status === 401) handleUnauthorized();
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
