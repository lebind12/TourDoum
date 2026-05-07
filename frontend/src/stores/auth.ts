import {
	clearAccessToken,
	getExpiresAt as readExpiresAt,
	setAccessToken,
} from "@/api/auth-token";
/**
 * Pinia auth store — JWT Bearer 호환층 (ADR-0011 FE-1).
 *
 * - access token은 본 store(reactive ref) + `api/auth-token` 모듈 스코프 양쪽에 보관.
 *   client.ts는 모듈 스코프를 읽어 Bearer를 부착(순환 import 회피).
 * - localStorage/sessionStorage 사용 금지(ADR-0011). 새로고침 시 메모리가 비면 라우터 가드가
 *   /api/me 401을 만나 /login으로 redirect → "다시 로그인" UX.
 * - refresh 자동 갱신은 BE-2(`POST /api/auth/refresh`) 머지 후 본 store에 보강한다.
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

/** be #61 응답 contract. */
interface LoginApiResponse {
	accessToken: string;
	expiresInSeconds: number;
	tokenType: string;
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
	/** epoch ms — 만료 시각. 0 = 미설정. */
	const expiresAt = ref<number>(0);
	const loading = ref(false);
	const error = ref<string | null>(null);

	/** 토큰을 모듈 + store 양쪽에 박제. */
	function applyToken(token: string, expiresInSeconds: number): void {
		setAccessToken(token, expiresInSeconds);
		accessToken.value = token;
		expiresAt.value = readExpiresAt();
	}

	/** 메모리/스토어를 모두 비운다. 401 핸들러와 logout이 호출. */
	function clearSession(): void {
		clearAccessToken();
		accessToken.value = null;
		expiresAt.value = 0;
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
			// 401 핸들러가 별도로 clearSession()을 호출하므로 여기서는 user만 비운다.
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

		// 회원가입 응답은 토큰을 발급하지 않으므로 명시 로그인.
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

		// be #61 contract: 200 + {accessToken, expiresInSeconds, tokenType, user}.
		// 빈 응답은 silent fallback 금지 — 명시적 에러 처리.
		if (
			!result.data ||
			typeof result.data.accessToken !== "string" ||
			!result.data.user
		) {
			error.value = "로그인 응답 형식이 올바르지 않습니다.";
			return false;
		}

		applyToken(result.data.accessToken, result.data.expiresInSeconds);
		currentUser.value = result.data.user;
		return true;
	}

	async function logout(): Promise<void> {
		loading.value = true;
		error.value = null;

		// BE-2 머지 전에는 stub(204). 응답 무관하게 클라이언트 메모리 클리어.
		await post<null>("/api/auth/logout", {});

		clearSession();
		loading.value = false;
	}

	return {
		currentUser,
		accessToken,
		expiresAt,
		loading,
		error,
		fetchMe,
		signup,
		login,
		logout,
		clearSession,
	};
});
