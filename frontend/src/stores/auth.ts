import {
	clearAccessToken,
	getRefreshToken,
	getExpiresAt as readExpiresAt,
	getRefreshExpiresAt as readRefreshExpiresAt,
	setAccessToken,
	setRefreshToken,
} from "@/api/auth-token";
/**
 * Pinia auth store — JWT Bearer 호환층 (ADR-0011 FE-1 + FE-1 보강).
 *
 * - access/refresh token 은 본 store(reactive ref) + `api/auth-token` 모듈 스코프 양쪽에 보관.
 *   client.ts 는 모듈 스코프를 읽어 Bearer 부착(순환 import 회피).
 * - localStorage/sessionStorage 사용 금지(ADR-0011). 새로고침 시 메모리가 비면 라우터 가드가
 *   /api/me 401 을 만나 /login 으로 redirect → "다시 로그인" UX.
 * - 401 만료 응답 시 client.ts 가 `tryRefresh()` 로 본 store 의 `refresh()` 액션을 호출.
 *   `main.ts` 가 등록한 핸들러가 진입점 — 본 store 는 핸들러 본체만 노출.
 *
 * BE-3(httpOnly cookie) 머지 후 cleanup 대상:
 * - refreshToken 메모리 보관 + body 동봉 제거 (cookie 자동 전송 의존).
 */
import { get, post } from "@/api/client";
import { defineStore } from "pinia";
import { ref } from "vue";

export interface User {
	id: number;
	email: string;
	nickname: string;
	role: string;
	createdAt?: string;
}

/** be #63 BE-2 응답 contract. */
interface LoginApiResponse {
	accessToken: string;
	expiresInSeconds: number;
	tokenType: string;
	refreshToken: string;
	refreshExpiresInSeconds: number;
	user: User;
}

interface SignupPayload {
	email: string;
	password: string;
	nickname: string;
}

interface LoginPayload {
	email: string;
	password: string;
}

export const useAuthStore = defineStore("auth", () => {
	const currentUser = ref<User | null>(null);
	/** 메모리 캐시(reactive). 진실은 `api/auth-token` 모듈과 동기화된다. */
	const accessToken = ref<string | null>(null);
	/** epoch ms — access 만료 시각. 0 = 미설정. */
	const expiresAt = ref<number>(0);
	/** refreshToken 표면 (BE-3 머지 후 제거 예정). */
	const refreshToken = ref<string | null>(null);
	const refreshExpiresAt = ref<number>(0);
	const loading = ref(false);
	const error = ref<string | null>(null);

	/** access 토큰을 모듈 + store 양쪽에 박제. */
	function applyAccessToken(token: string, expiresInSeconds: number): void {
		setAccessToken(token, expiresInSeconds);
		accessToken.value = token;
		expiresAt.value = readExpiresAt();
	}

	/** refresh 토큰을 모듈 + store 양쪽에 박제. */
	function applyRefreshToken(token: string, expiresInSeconds: number): void {
		setRefreshToken(token, expiresInSeconds);
		refreshToken.value = token;
		refreshExpiresAt.value = readRefreshExpiresAt();
	}

	/** 메모리/스토어를 모두 비운다. 401 핸들러와 logout이 호출. */
	function clearSession(): void {
		clearAccessToken();
		accessToken.value = null;
		expiresAt.value = 0;
		refreshToken.value = null;
		refreshExpiresAt.value = 0;
		currentUser.value = null;
	}

	async function fetchMe(): Promise<boolean> {
		// 토큰이 없으면 굳이 401을 받으러 가지 않는다.
		if (accessToken.value === null) {
			currentUser.value = null;
			return false;
		}

		loading.value = true;
		error.value = null;

		const result = await get<User>("/api/me");

		loading.value = false;

		if (result.error || !result.data) {
			currentUser.value = null;
			return false;
		}

		currentUser.value = result.data;
		return true;
	}

	async function signup(payload: SignupPayload): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await post<unknown>("/api/members/signup", payload);

		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		return login({ email: payload.email, password: payload.password });
	}

	async function login(payload: LoginPayload): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await post<LoginApiResponse>("/api/auth/login", payload);

		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		// be #63 BE-2 contract:
		// 200 + {accessToken, expiresInSeconds, tokenType, refreshToken, refreshExpiresInSeconds, user}.
		// 빈 응답 / 누락 필드 = silent fallback 금지.
		if (
			!result.data ||
			typeof result.data.accessToken !== "string" ||
			typeof result.data.refreshToken !== "string" ||
			!result.data.user
		) {
			error.value = "로그인 응답 형식이 올바르지 않습니다.";
			return false;
		}

		applyAccessToken(result.data.accessToken, result.data.expiresInSeconds);
		applyRefreshToken(
			result.data.refreshToken,
			result.data.refreshExpiresInSeconds,
		);
		currentUser.value = result.data.user;
		return true;
	}

	/**
	 * 401 만료 응답 시 client.ts 의 `tryRefresh()` 가 호출하는 진입점 (main.ts에서 등록).
	 *
	 * 동작:
	 * - 메모리 refresh token 이 없으면 false (호출자는 unauthorized 흐름 진행).
	 * - `POST /api/auth/refresh` 에 body `{refreshToken}` 동봉. `skipRefresh=true` 로
	 *   refresh 자체의 401 → 재진입을 차단.
	 * - 성공 시 새 LoginResponse 로 access/refresh 모두 갱신. user 는 변경 없음.
	 * - 실패 시 clearSession (호출자는 후속 notifyUnauthorized 실행).
	 */
	async function refresh(): Promise<boolean> {
		const current = getRefreshToken();
		if (!current) {
			clearSession();
			return false;
		}

		const result = await post<LoginApiResponse>(
			"/api/auth/refresh",
			{ refreshToken: current },
			{ skipRefresh: true },
		);

		if (result.error || !result.data) {
			clearSession();
			return false;
		}
		if (
			typeof result.data.accessToken !== "string" ||
			typeof result.data.refreshToken !== "string"
		) {
			clearSession();
			return false;
		}

		applyAccessToken(result.data.accessToken, result.data.expiresInSeconds);
		applyRefreshToken(
			result.data.refreshToken,
			result.data.refreshExpiresInSeconds,
		);
		// /api/auth/refresh 도 user 를 반환 — 최신 권한 동기화.
		if (result.data.user) currentUser.value = result.data.user;
		return true;
	}

	async function logout(): Promise<void> {
		loading.value = true;
		error.value = null;

		// BE-2 contract: Bearer 인증 + body {refreshToken?} → 204.
		// Bearer 는 client.ts 가 자동 부착. body 의 refreshToken 동봉으로 family revoke 명시.
		const current = getRefreshToken();
		await post<null>(
			"/api/auth/logout",
			current ? { refreshToken: current } : {},
			{ skipRefresh: true },
		);

		clearSession();
		loading.value = false;
	}

	return {
		currentUser,
		accessToken,
		expiresAt,
		refreshToken,
		refreshExpiresAt,
		loading,
		error,
		fetchMe,
		signup,
		login,
		logout,
		refresh,
		clearSession,
	};
});
