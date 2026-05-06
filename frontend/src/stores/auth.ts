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
	const loading = ref(false);
	const error = ref<string | null>(null);

	async function fetchMe(): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await get<User>("/api/me");

		loading.value = false;

		if (result.error) {
			currentUser.value = null;
			return false;
		}

		currentUser.value = result.data;
		return true;
	}

	async function signup(payload: SignupPayload): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await post<User>("/api/members/signup", payload);

		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		// 회원가입 후 세션이 자동 발급되지 않을 수 있으므로 명시적으로 로그인
		return login({ email: payload.email, password: payload.password });
	}

	async function login(payload: LoginPayload): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await post<User>("/api/auth/login", payload);

		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		// 로그인 성공 후 /api/me 로 사용자 정보를 채운다
		if (result.data) {
			currentUser.value = result.data;
		} else {
			await fetchMe();
		}

		return true;
	}

	async function logout(): Promise<void> {
		loading.value = true;
		error.value = null;

		await post<null>("/api/auth/logout", {});

		currentUser.value = null;
		loading.value = false;
	}

	return { currentUser, loading, error, fetchMe, signup, login, logout };
});
