# Vercel Preview URL Allowlist + CORS 정책

> ADR-0011 §"Cookie 정책 cross-origin 갱신" + ADR-0013 §"Cross-origin 정책 영향" 운영 구현물. **BE 측 enforcement는 별 BE task** (BE-CORS-VE1 권고).

## 1. Vercel preview URL 패턴

Vercel은 PR commit별로 preview deploy를 자동 생성한다.

```text
project: tourdoum (또는 tourdoum-frontend 충돌 시)
team:    <vercel-team-slug>      (개인 계정이면 username)

production:        tourdoum-<team>.vercel.app
                   또는 tourdoum.vercel.app  (project name 단독)

preview:           tourdoum-<git-sha-prefix>-<team>.vercel.app
                   ex) tourdoum-abc1234-lebind12.vercel.app

branch alias:      tourdoum-git-<branch-slug>-<team>.vercel.app
                   ex) tourdoum-git-feat-auth-lebind12.vercel.app
```

`<git-sha-prefix>` = SHA 7자리 영소문자/숫자.
`<branch-slug>` = branch name kebab-case (`/` → `-`).
`<team>` = Vercel team slug.

## 2. 보안 함정 — Fork PR preview

**가장 큰 위험**: 외부 contributor가 fork → PR 시 Vercel preview는 동일 패턴으로 생성된다. 이 preview에서 `fetch(<BE>, {credentials:'include'})` 를 호출하면 BE가 CORS allowlist 매칭만으로 cookie를 발급할 수 있다.

대응 (택일 또는 조합):

### 대응 A — Vercel Deployment Protection (권장 Phase 1)

Vercel Project → Settings → Deployment Protection → **Standard Protection** = ON.

- Preview deploy 접근 시 Vercel SSO 인증 요구 → 외부 사용자 차단
- Production은 영향 X
- Free tier 가능 (Pro만 일부 옵션)

### 대응 B — BE 측 fork 차단 (보강)

GitHub Actions 또는 Vercel webhook으로 PR head fork 여부 감지 → fork preview URL을 별도 `forked-preview` 환경변수에 박제하지 않음. BE는 본 환경변수가 없으면 preview origin 거부.

복잡도 높음 → Phase 2 보류 권고.

### 대응 C — BE 측 `Origin` 검증 + cookie 미발급 (보강)

CORS allowlist 매칭은 통과시키되, JWT 또는 cookie 발급 직전 `Origin` 추가 검증:

- Production origin → cookie 발급
- Preview origin → cookie 발급 거부, 명시 토큰 헤더만 허용

ADR-0011 §"Cookie 정책 cross-origin 갱신" 본문에 본 정책 박제 권고.

## 3. BE CORS allowlist regex

Spring Boot `WebMvcConfigurer` 또는 `CorsConfigurationSource`에 박제할 regex:

```text
# Production
^https://tourdoum(\-[a-z0-9-]+)?\.vercel\.app$

# Preview (sha)
^https://tourdoum-[a-z0-9]{6,12}-[a-z0-9-]+\.vercel\.app$

# Preview (branch alias)
^https://tourdoum-git-[a-z0-9-]+-[a-z0-9-]+\.vercel\.app$

# Local dev
^http://localhost:(5173|30173)$
```

권장 단일 regex:

```regex
^https://tourdoum(\-[a-z0-9-]+){0,3}\.vercel\.app$|^http://localhost:(5173|30173)$
```

Spring Boot 예시 (참고용 — 실 박제는 BE-CORS-VE1):

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of(
        "https://tourdoum*.vercel.app",       // Spring pattern (regex 아님)
        "http://localhost:5173",
        "http://localhost:30173"
    ));
    cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setAllowCredentials(true);
    cfg.setExposedHeaders(List.of("X-CSRF-TOKEN"));
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
}
```

`AllowedOriginPatterns`는 Spring Boot 2.4+에서 wildcard + credentials 동시 허용. 정규식 더 엄격히 하려면 `CorsConfigurationSource` 커스텀 구현.

## 4. application.yml 환경변수화 권고

```yaml
app:
  cors:
    allowed-origin-patterns:
      - "https://tourdoum*.vercel.app"
      - "http://localhost:5173"
      - "http://localhost:30173"
    allow-credentials: true
```

ACA secrets / dev profile 별로 override 가능.

## 5. 검증 체크리스트 (BE-CORS-VE1)

- [ ] Vercel production URL → preflight `OPTIONS` 200 + `Access-Control-Allow-Origin` 정확
- [ ] Vercel preview URL (sha) → 동일 통과
- [ ] 임의 `evil.vercel.app` → preflight 차단 (`Origin` 헤더 강제 wildcard 매칭 회피 검증)
- [ ] `localhost:5173` dev → 통과
- [ ] cookie `Set-Cookie: SameSite=None; Secure` 정확
- [ ] HTTP (non-HTTPS) origin → 차단 (Production)
- [ ] Fork PR preview URL → 대응 A/B/C 중 택일 동작 확인

## 6. 미해결

- Custom domain 도입 시 regex 갱신 필요 (`tourdoum.dev` 등).
- ACA endpoint도 cross-origin (Vercel ↔ ACA). BE 자체에서 ACA fqdn 본 origin은 매칭 X (서버 자체 origin은 BE side 처리).
- WebSocket (`Chat WS Phase 2`) Origin 검증 — Spring `WebSocketConfigurer`에서 동일 allowlist 적용 필요. 별 BE task.
