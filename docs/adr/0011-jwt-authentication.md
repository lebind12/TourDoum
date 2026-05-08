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

### BE-4 비밀번호 보안 강화 (2026-05-07 #3 회차 박제, researcher #6 + Codex 교차)

BE-2(refresh family + denylist) 이후 병렬 가능. password 변경/reset 시 token revocation hook 호출이 필수 연결점.

#### 7-box 분해

```
BE-4.1 PasswordEncoder 알고리즘
   - Argon2id (m=64MiB, t=3, p=1) 1차 + bcrypt(cost=12) fallback (DelegatingPasswordEncoder).
   - 벤치 실패(p99 > 200ms 등) 시 Argon2id m=32MiB, t=3 까지 허용.
   - DelegatingPasswordEncoder 형식: `{id}encodedPassword` — 예: `{argon2id}$argon2id$v=19$m=65536,t=3,p=1$...` / `{bcrypt}$2a$12$...`. id prefix와 알고리즘 자체 인코딩은 분리. (Codex 4회차 정정)

BE-4.2 Password Policy
   - 최소 12자, 최대 ≥ 64자, 공백·유니코드 허용. **본 정책은 프로젝트 자율 정책이며 NIST 800-63B Rev.4 단일비밀번호 최소 15자 기준은 미준수** (학습 단계 trade-off).
   - HIBP top-N + 자체 blocklist 차단. ID·이메일 유사값 차단.
   - 정기 변경 강제 X (NIST 800-63B Rev.4 기준).
   - 3종 결합 강제 X (KISA 8자/3종은 옵션이지 의무 아님).

BE-4.3 Revocation Hook
   - password 변경/reset 성공 즉시 트리거: refresh family 폐기 + access denylist + 전 디바이스 logout.
   - BE-2의 RefreshTokenStore.revokeFamily + AccessTokenDenylist.add 재사용. 새 코드 최소.

BE-4.4 Brute-force 방어
   - per-account 5회 실패 → 30분 자동 해제 잠금.
   - per-IP 10회/10분 throttling 병행.
   - 관리자 계정은 수동 해제만 (DoS 노출 시에도 시간 자동 해제 X).

BE-4.5 Reset 플로우
   - 토큰 30분 1회용 + 해시 저장(plain X) + 사용 즉시 무효 + revocation hook 호출.
   - 본인확인은 학습 단계에선 이메일 콘솔 출력 mock.

BE-4.6 Migration
   - 기존 BCrypt strength 10 사용자 → 로그인 성공 시 Argon2id rehash on login.
   - DelegatingPasswordEncoder의 upgradeEncoding 활용.

BE-4.7 한국 기준 매핑 표 박제 (본 ADR §"근거"에 별도 추가)
```

#### 4회차 후속 — Cookie 정책 cross-origin 갱신 (Vercel + ACA)

ADR-0013 §8 결정으로 prod-lite 배포 토폴로지가 **Vercel `*.vercel.app` 프론트엔드 + Azure Container Apps `*.azurecontainerapps.io` 백엔드**로 확정됨. 두 도메인은 cross-site이므로 본 ADR 원안 `SameSite=Strict`는 cookie 인증이 작동하지 않는다. 다음으로 갱신:

- **prod-lite cookie**: `HttpOnly; Secure; SameSite=None`. `SameSite=Strict`는 같은 root domain(예: `tourdoum.example.com` 도입) 운영 프로필에서만 허용.
- **CORS**: credentials 쓰는 cross-site에서 `Access-Control-Allow-Origin: *` **금지**. 정확히 허용된 Origin만 반사 + `Access-Control-Allow-Credentials: true`. allowlist 관리:
  - 운영 origin (확정 도메인)
  - 승인된 Vercel preview URL (commit/branch별 generated URL — `<project>-<hash>-<team>.vercel.app` 패턴, regex 또는 수동 승인)
  - local dev origin (`http://localhost:5173`)
- **Origin/Referer 검증**: 모든 상태 변경 cookie 요청에 적용.
- **CSRF token cookie**: non-HttpOnly (JS read 필수). access/refresh cookie는 HttpOnly로 분리. signed double-submit 또는 `X-XSRF-TOKEN` header 둘 중 하나로 검증.
- **Bearer API**: 별도 transport. cookie CSRF 정책은 cookie 인증 요청에만 적용.
- **함정**: 브라우저 third-party cookie 정책 변화 추적 (Chrome/Safari ITP). prod-lite는 cross-site cookie 의존이므로 추후 same root domain 이행 backlog.

본 갱신은 ADR-0013 §"Cross-origin 정책 영향"과 한 쌍. researcher #8 보고 + Codex 검증 반영.

#### 법정 vs 프로젝트 정책 분리 (Codex 교차 검토 반영)

- **법정 의무 (개보위 고시 제2025-9호 §7, 2025-10-31 시행)**: 비밀번호의 일방향 저장.
- **법정 의무 (§5(6))**: "일정 횟수 이상 인증 실패 시 접근 제한". **5회는 해설/심사 예시이지 법령 본문 수치 아님**.
- **프로젝트 정책 (법정 수치 X)**: 12자, m=64MiB, 5회/30분, IP 10회/10분, reset 30분.

#### 출처

- [KISA 패스워드 선택 및 이용 안내서](https://www.kisa.or.kr/2060305/form?postSeq=14&lang_type=KO) — 2종 10자 / 3종 8자, 2019.06 개정.
- [개인정보 안전성 확보조치 기준 고시 2025-9호](https://www.law.go.kr/LSW//admRulInfoP.do?admRulSeq=2100000265956&chrClsCd=010201) — §7 일방향 저장, §5(6) 인증 실패 제한.
- [개보위 안내서 2024.10](https://www.privacy.go.kr/front/bbs/bbsView.do?bbsNo=BBSMSTR_000000000049&bbscttNo=20767)
- [ISMS-P 인증기준 2.5.4](https://meganad.github.io/ISMS-P/CERT/2.5.4) — 변경 주기 조직 자율, 임시 PW 후 강제 변경 의무.
- [NIST SP 800-63B Rev.4 (2025-08)](https://pages.nist.gov/800-63-4/sp800-63b.html) — 단독 15자, MFA 8자, 정기 변경 강제 금지.
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — Argon2id 최소 m=19MiB,t=2,p=1.

researcher 보고: `/tmp/researcher-be4-report.md`. Codex 교차 검토 로그: `.codex-20260507T230513Z.log`.

## 참고

- [OWASP JWT for Java Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [RFC 9700 OAuth 2.0 Security BCP](https://datatracker.ietf.org/doc/draft-ietf-oauth-security-topics/)
- [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Spring Session Redis](https://docs.spring.io/spring-session/reference/guides/boot-redis.html)

Codex CLI 회의 로그: `/20-spec-tourdoum/.codex-20260507T093509Z.log` (참고용).
