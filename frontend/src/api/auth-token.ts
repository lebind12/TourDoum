/**
 * 모듈 스코프 access/refresh token 보관소 (ADR-0011 FE-1 + FE-1 보강).
 *
 * 목적:
 * 1. `api/client.ts`가 Pinia store를 직접 import하지 않도록 토큰을 한 단계 분리(순환 import 회피).
 * 2. localStorage/sessionStorage **금지** — 본 모듈은 메모리(closure)에만 보관한다.
 *    페이지 새로고침 시 소실되며, 그 경우 "다시 로그인" UX가 ADR-0011 정책.
 * 3. 401 응답 핸들러를 `main.ts`에서 등록 → client.ts가 호출. router/store에 의존하지 않는다.
 *
 * BE-2(be #63) 머지 후 보강:
 * - refreshToken / refreshExpiresAt 메모리 박제.
 * - `registerRefreshHandler(fn)` 로 외부 주입한 refresh 액션을 `tryRefresh()` 로 호출.
 *   동시 401 다발 시 in-flight Promise 단일화 → refresh 1회만 발사.
 *
 * BE-3(httpOnly cookie) 머지 후 cleanup 대상:
 * - refreshToken 메모리 보관 제거 (cookie 자동 전송 의존).
 * - tryRefresh 는 body 없이 호출 (cookie 만 사용).
 */

let accessToken: string | null = null;
/** epoch ms — access 토큰 만료 시각. 0 = 미설정. */
let expiresAt = 0;
let refreshToken: string | null = null;
/** epoch ms — refresh 토큰 만료 시각. 0 = 미설정. */
let refreshExpiresAt = 0;

let unauthorizedHandler: (() => void) | null = null;
let refreshHandler: (() => Promise<boolean>) | null = null;

/** in-flight refresh promise (race 방지: 동시 401 → refresh 1회). */
let inflightRefresh: Promise<boolean> | null = null;

/** 로그인 성공 시 응답의 `accessToken` + `expiresInSeconds`를 그대로 박제. */
export function setAccessToken(token: string, expiresInSeconds: number): void {
	accessToken = token;
	expiresAt = Date.now() + Math.max(0, expiresInSeconds) * 1000;
}

/** 로그인/refresh 성공 시 refreshToken + 만료 박제. BE-3 머지 후 제거 예정. */
export function setRefreshToken(token: string, expiresInSeconds: number): void {
	refreshToken = token;
	refreshExpiresAt = Date.now() + Math.max(0, expiresInSeconds) * 1000;
}

/** 로그아웃 / 401 / 명시적 세션 종료 시 호출. access + refresh 둘 다 비운다. */
export function clearAccessToken(): void {
	accessToken = null;
	expiresAt = 0;
	refreshToken = null;
	refreshExpiresAt = 0;
}

/** axios/fetch interceptor가 매 요청마다 호출. 토큰 없으면 null → Authorization 미부착. */
export function getAccessToken(): string | null {
	return accessToken;
}

/** logout body / refresh body 작성 시 사용. */
export function getRefreshToken(): string | null {
	return refreshToken;
}

/** 호출자가 만료 임박을 감지해 사전 refresh를 트리거할 때 사용. */
export function getExpiresAt(): number {
	return expiresAt;
}

export function getRefreshExpiresAt(): number {
	return refreshExpiresAt;
}

/**
 * 401 응답 시 호출할 핸들러를 등록한다. `main.ts`에서 pinia/router init 직후 1회 호출.
 * 본 모듈은 vue-router/pinia에 의존하지 않으므로 콜백을 외부 주입 받는다.
 */
export function registerUnauthorizedHandler(fn: () => void): void {
	unauthorizedHandler = fn;
}

/** client.ts가 401 응답을 만났을 때 호출. 핸들러 미등록 시 no-op. */
export function notifyUnauthorized(): void {
	if (unauthorizedHandler) unauthorizedHandler();
}

/**
 * refresh 핸들러 등록. `main.ts`에서 auth store의 refresh 액션을 주입한다.
 * 핸들러는 성공 시 access/refresh 토큰을 본 모듈에 set 한 뒤 true 를 반환한다.
 */
export function registerRefreshHandler(fn: () => Promise<boolean>): void {
	refreshHandler = fn;
}

/**
 * client.ts가 401 만료 응답을 만났을 때 호출. 동시 다발 401에서도 refresh 는 1회만 호출되며
 * 모든 호출자에게 동일 Promise 결과를 공유한다.
 *
 * 반환:
 * - `true` — refresh 성공, 호출자는 새 access token 으로 원 요청 재시도 가능.
 * - `false` — refresh 핸들러 미등록 또는 실패. 호출자는 unauthorized 흐름 진행.
 */
export function tryRefresh(): Promise<boolean> {
	if (!refreshHandler) return Promise.resolve(false);
	if (refreshToken === null) return Promise.resolve(false);
	if (inflightRefresh) return inflightRefresh;

	const handler = refreshHandler;
	inflightRefresh = handler()
		.catch(() => false)
		.finally(() => {
			inflightRefresh = null;
		}) as Promise<boolean>;
	return inflightRefresh;
}

/** 테스트 헬퍼 — in-flight 상태 강제 초기화. */
export function _resetRefreshState(): void {
	inflightRefresh = null;
}
