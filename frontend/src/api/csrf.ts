/**
 * CSRF double-submit helper (ADR-0011 BE-3 / FE-2).
 *
 * BE `CookieCsrfTokenRepository.withHttpOnlyFalse()` 가 매 응답에서 `XSRF-TOKEN` cookie 를
 * materialize 한다(httpOnly=false → JS 가독). SPA 는 mutation 요청 시 이 값을 그대로
 * `X-XSRF-TOKEN` 헤더로 echo 해야 BE 의 plain `CsrfTokenRequestAttributeHandler` 가 통과시킨다.
 *
 * BE 는 login/signup 만 csrf().ignoringRequestMatchers 로 면제. 그 외 모든 mutation 은 헤더 필수.
 *
 * 참고:
 * - cookie 값은 (Spring 기본) 그냥 ASCII UUID 라 보통 인코딩 영향 없음. 안전 차원에서
 *   `decodeURIComponent` 로 통과.
 * - `document` 미지원 환경(SSR 등)에서는 `null` 반환 — 호출자가 헤더 미부착으로 흘려보냄.
 */

export const CSRF_COOKIE_NAME = "XSRF-TOKEN";
export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

const MUTATION_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/** 주어진 HTTP method 가 CSRF 헤더 부착 대상인지 판정. GET/HEAD/OPTIONS 면제. */
export function isMutation(method: string): boolean {
	return MUTATION_METHODS.has(method.toUpperCase());
}

/**
 * `document.cookie` 에서 `XSRF-TOKEN` 값을 읽는다. 없으면 `null`.
 * 호출자(client.ts) 는 null 일 때 헤더를 부착하지 않는다 — BE 가 면제 path 가 아니면 403.
 */
export function readXsrfToken(): string | null {
	if (typeof document === "undefined" || typeof document.cookie !== "string") {
		return null;
	}
	const cookies = document.cookie ? document.cookie.split("; ") : [];
	const prefix = `${CSRF_COOKIE_NAME}=`;
	for (const c of cookies) {
		if (c.startsWith(prefix)) {
			const raw = c.slice(prefix.length);
			try {
				return decodeURIComponent(raw);
			} catch {
				return raw;
			}
		}
	}
	return null;
}
