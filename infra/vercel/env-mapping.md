# Vercel Environment Variables — 명세

> Vercel은 환경변수를 3개 환경(`Production` / `Preview` / `Development`)별로 분리 등록 가능. Vite는 빌드 시점에 `VITE_*` 접두사만 client bundle에 inline.

## 1. 환경별 매핑 표

| Key | Production | Preview | Development | 비고 |
|---|---|---|---|---|
| `VITE_API_BASE_URL` | `https://<aca-fqdn>.koreacentral.azurecontainerapps.io` | (Phase 1) 동일 / (Phase 2) `https://<dev-aca>...` | `http://localhost:30080` (BE worktree) 또는 `http://localhost:8080` | INFRA-AZ-2 끝난 뒤 ACA fqdn 박제 |
| `VITE_TOSS_CLIENT_KEY` | `test_ck_*` mock key | 동일 | 동일 | mock 결제. 운영 진입 시 secret 등록 |
| `VITE_KAKAO_MAP_APP_KEY` | `<kakao-js-key>` | 동일 | 동일 | client-side, public OK (도메인 등록 필수) |
| `VITE_FEATURE_FLAGS_DEV` | `false` | `true` | `true` | dev-only UI toggle |
| `VITE_SENTRY_DSN` | (선택) | 동일 | (비활성) | Phase 2 보류 |
| `VITE_APP_VERSION` | `${VERCEL_GIT_COMMIT_SHA}` | 동일 | `dev` | Vercel system env 자동 주입 |

## 2. Vercel system env (자동 주입)

본 명세에 별도 등록 X — Vercel이 빌드 환경에 자동 주입. 활용 가능:

- `VERCEL_GIT_COMMIT_SHA` — 빌드 시 SHA. `VITE_APP_VERSION`에 매핑 권장.
- `VERCEL_GIT_COMMIT_REF` — branch name. preview는 PR branch.
- `VERCEL_URL` — 현재 deploy URL (preview 매번 다름).
- `VERCEL_ENV` — `production` / `preview` / `development`.

`vite.config.ts` 또는 빌드 스크립트에서 `VITE_VERCEL_*` 로 다시 쓰기 필요 (Vite는 `VITE_` 접두사만 client에 노출).

## 3. 등록 절차

### dashboard

1. Project → Settings → **Environment Variables**
2. **Add New** → Key/Value/환경 선택 (Production/Preview/Development 다중 체크)
3. **Encrypt** = ON (Vite 빌드 inline 시점에는 평문이므로 client-public 전제 — secret용 키는 본 매핑에 박제 X)

### CLI

```bash
# project linked 상태에서 (frontend/ 에서 vercel link 1회)
vercel env add VITE_API_BASE_URL production
# (값은 stdin 으로 입력)

# 일괄 export (.env 으로)
vercel env pull .env.local
```

## 4. 시크릿 정책

- 본 문서에 **실 키 commit X**.
- `test_ck_*` 같은 mock public key는 commit OK.
- BE에서 검증하는 내부 key (JWT private key, MySQL password 등) 는 **Vercel에 박제 X** — Vercel은 client-side만 책임. BE는 ACA secrets / Key Vault.
- Toss 운영 키 / Sentry DSN private project 등은 운영 진입 시점에만 등록.

## 5. Vite 코드 측 사용

```ts
// frontend/src/lib/env.ts (예시)
const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
```

타입 안전성: `frontend/src/vite-env.d.ts` 에 `interface ImportMetaEnv` 박제 (별 BE/FE task).

## 6. 후속

- INFRA-AZ-2 후 `VITE_API_BASE_URL` Production 값 박제 (ACA fqdn 확정 시점)
- `preview-allowlist-policy.md` §3 BE allowlist 갱신과 페어링
