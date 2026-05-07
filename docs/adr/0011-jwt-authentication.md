# ADR 0011 — JWT 인증 전환 (Spring Session 폐기)

- 작성일: 2026-05-07
- 상태: Accepted
- 관련: ADR-0003 (Spring Session/Form Login, 본 ADR로 대체)

## 컨텍스트

ADR-0003에서 Spring Security 6 + 폼 로그인 + Spring Session Data Redis로 인증을 박제했다. 학습은 충분히 진행됐고 자산도 안정. 다음 학습 단계로 다음 4가지를 의식적으로 다루기 위해 인증 모델을 JWT로 전환한다.

1. **JWT/refresh/rotation/revocation 전 사이클** — 학습 의의(SSAFY 표준).
2. **30만 RPS 분산 시뮬** — stateless access token이 sticky session 제거에 유리.
3. **모바일/외부 API 가능성** — cookie 외 transport(Bearer header) 표준화.
4. **인증 모델 마이그레이션 자체** — `HttpSession`/`@SessionAttribute` → `@AuthenticationPrincipal` 표준화.

## 결정

**옵션 C — Spring Session 인증 상태 폐기, JWT 단독 + per-request stateless 인증.** 5가지 결정 항목을 다음 조합으로 박제한다.

| 항목 | 결정 |
|---|---|
| 거취 | Spring Session 인증 폐기. JWT 단독. "stateless"는 access token 검증이 세션 조회에 의존하지 않는다는 뜻으로 한정. refresh rotation/logout/password-change revocation을 위한 Redis 상태는 허용. |
| 토큰 저장(브라우저 SPA) | `httpOnly + Secure + SameSite=Strict` cookie. localStorage/sessionStorage **금지**. CSRF 대응 필수. |
| 토큰 저장(모바일/외부 API) | 같은 JWT를 `Authorization: Bearer` header로 받음. SPA 표준 경로/테스트는 cookie 기반으로 고정. |
| Refresh | access 15분 + refresh 14일. **refresh rotation**(매 refresh마다 새 값) + **token family**(이전 refresh 재사용 시 family 전체 폐기) + **Redis denylist**(logout/password change 시). |
| 알고리즘 | **RS256** + `kid` 기반 key rotation. 첫 구현은 active key 1 + previous key 검증 허용 구조. |
| Redis 역할 | 컨테이너 유지. Spring Session 인증 저장소 역할 제거. refresh family/denylist/login throttling/password reset 보안 상태 저장소로 재활용. |

## 근거 (요약)

### 거취 — 옵션 C
옵션 B(병행)는 단기 회귀 비용은 낮지만 웹=session, 외부 API=JWT 이중 인증면을 만들고, 이후 권한 버그·테스트 분기·문서 분기가 누적된다. 학습 회차 목적(JWT/refresh/rotation/revocation 전체 사이클)을 병행은 우회한다.

### 토큰 저장 — httpOnly cookie
localStorage/sessionStorage는 XSS 시 탈취면이 큼. httpOnly cookie는 JS가 토큰을 읽지 못하게 함. cookie는 자동 전송이라 CSRF 대응(SameSite, Origin/Referer 검증, CSRF header 또는 signed double-submit cookie)을 명시 요구사항으로 둔다.

### Refresh — rotation + family + denylist
access만 발급은 학습 가치 낮음. blacklist 없는 단순 만료는 logout/password change/탈취 대응이 약함. rotation + family는 "완전 무상태는 logout/revocation 요구와 충돌한다"는 학습 포인트를 정확히 다룬다.

### 알고리즘 — RS256
HS256은 단일 서비스에 충분하지만 검증자가 늘면 shared secret 배포가 위험. RS256은 private key를 발급 서버에만 두고 public key를 검증자에게 배포. gateway/resource server 분리·외부 검증·30만 RPS 시뮬과 일관.

### 기존 자산 처리
Redis는 보안 상태 저장소로 재활용(refresh family, denylist, throttling, password reset). `HttpSession`/`@SessionAttribute` 의존 코드는 `@AuthenticationPrincipal` + `CurrentUser` resolver로 표준화.

## 결과

### Positive
- stateless access token으로 분산 시뮬에 유리.
- cookie + CSRF로 XSS 차단 + 명시적 CSRF 학습.
- rotation/family/denylist로 revocation 학습.
- RS256으로 key rotation/책임 분리 학습.
- Redis 자산 폐기 X — 보안 인프라로 재활용.

### Negative (회귀 비용 매우 높음)
- Playwright 8 spec — login/refresh/logout/만료 재검증.
- vitest 141 — auth mock 전수 재분류(cookie session vs 401/refresh interceptor).
- `HttpSession`/`@SessionAttribute` 의존 코드 마이그레이션 다수.
- BE-2(rotation/family) + FE-1(refresh race) 구현 복잡도 증가.
- RS256은 HS256 대비 설정/테스트 키 관리 + 검증 비용 증가 → 30만 RPS 시뮬에서 관측 대상.

## 후속 ADR / Task

### ADR
- ADR-0003 — `Status: Superseded by ADR-0011` 표기.
- ADR-0012 후보 — 채팅 keyset paging (Group 2).
- ADR-0013 후보 — K6 부하 시뮬 + 분산 설계 (Group 3, JWT 검증 비용 관측 포함).

### Task 분해 (의존 순서)

```
BE-1 인증 기반 교체 (JWT filter + AuthenticationPrincipal 표준화 + HttpSession 의존 제거)
   ↓
BE-2 토큰/Redis 보안 상태 (RS256 key + kid + access/refresh + rotation + family + denylist)
   ↓
BE-3 CSRF/cookie 정책 (Secure/SameSite/Path + Origin 검증 + CSRF header or double-submit)
   ↓
FE-1 axios 인증 흐름 (withCredentials 유지 + CSRF header + 401→refresh 1회 재시도 + race 방지)
   ↓
FE-2 화면/상태 회귀
   ↓
QA-1 테스트 자산 갱신 (Playwright cookie fixture + refresh/logout/revocation spec)
QA-2 회귀/부하 관측 (vitest auth mock 전수 + BE security IT + RS256 검증 비용)
```

BE-4 비밀번호 보안 강화는 BE-2 이후 병렬 가능. 단 비밀번호 변경 시 token revocation과 연결되므로 최종 QA 전 합류 필수.

## 참고

- [OWASP JWT for Java Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [RFC 9700 OAuth 2.0 Security BCP](https://datatracker.ietf.org/doc/draft-ietf-oauth-security-topics/)
- [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Spring Session Redis](https://docs.spring.io/spring-session/reference/guides/boot-redis.html)

Codex CLI 회의 로그: `/20-spec-tourdoum/.codex-20260507T093509Z.log` (참고용).
