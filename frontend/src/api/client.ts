/**
 * fetch 래퍼.
 * VITE_API_BASE_URL 환경 변수가 없으면 http://localhost:8080 으로 폴백.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface ApiResponse<T> {
	data: T | null;
	error: string | null;
}

export async function get<T>(path: string): Promise<ApiResponse<T>> {
	try {
		const res = await fetch(`${BASE_URL}${path}`);
		if (!res.ok) {
			return { data: null, error: `HTTP ${res.status}` };
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
