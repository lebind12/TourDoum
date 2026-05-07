/**
 * 모듈 스코프 access token 보관소 (ADR-0011 FE-1).
 *
 * 목적:
 * 1. `api/client.ts`가 Pinia store를 직접 import하지 않도록 토큰을 한 단계 분리(순환 import 회피).
 * 2. localStorage/sessionStorage **금지** — 본 모듈은 메모리(closure)에만 보관한다.
 *    페이지 새로고침 시 소실되며, 그 경우 "다시 로그인" UX가 ADR-0011 정책.
 * 3. 401 응답 핸들러를 `main.ts`에서 등록 → client.ts가 호출. router/store에 의존하지 않는다.
 *
 * refresh 자동 갱신은 BE-2(`POST /api/auth/refresh`) 머지 후 본 모듈의 401 핸들러에서 후속 호출로
 * 보강한다. 본 회차는 stub(메모리 클리어 + /login redirect)만.
 */

let accessToken: string | null = null;
/** epoch ms — 토큰 만료 시각. 0 = 미설정. */
let expiresAt = 0;
let unauthorizedHandler: (() => void) | null = null;

/** 로그인 성공 시 응답의 `accessToken` + `expiresInSeconds`를 그대로 박제. */
export function setAccessToken(token: string, expiresInSeconds: number): void {
	accessToken = token;
	expiresAt = Date.now() + Math.max(0, expiresInSeconds) * 1000;
}

/** 로그아웃 / 401 / 명시적 세션 종료 시 호출. */
export function clearAccessToken(): void {
	accessToken = null;
	expiresAt = 0;
}

/** axios/fetch interceptor가 매 요청마다 호출. 토큰 없으면 null → Authorization 미부착. */
export function getAccessToken(): string | null {
	return accessToken;
}

/** 호출자가 만료 임박을 감지해 사전 refresh를 트리거할 때 사용 (BE-2 후 보강 예정). */
export function getExpiresAt(): number {
	return expiresAt;
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
