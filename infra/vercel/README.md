# TourDoum Frontend on Vercel — Operations Guide

> 상태: ADR-0013 §8 결정 박제. 본 문서는 IaC + 운영 절차이며, **실 Vercel 프로젝트는 사용자 dashboard 액션으로 생성**한다 (API token 박제 X).

ADR-0013 §8: Frontend = Vercel (이전 SWA 폐기). Backend = Azure Container Apps (`api-tourdoum.<env>.azurecontainerapps.io`). Cross-origin은 ADR-0011 §"Cookie 정책 cross-origin 갱신" + ADR-0013 §"Cross-origin 정책 영향" 인계.

## 1. 환경 모델

| Vercel 환경 | URL 패턴 | 트리거 | API 대상 |
|---|---|---|---|
| **Production** | `tourdoum-<team>.vercel.app` 또는 default | `develop` push (또는 `main`) | prod-lite ACA |
| **Preview** | `tourdoum-<git-sha-prefix>-<team>.vercel.app` | PR open/update | prod-lite ACA (Phase 1) / dev ACA (Phase 2 보류) |
| **Development** | `localhost:5173` | `npm run dev` | local Spring Boot (또는 prod-lite ACA via VITE_API_BASE_URL) |

핵심 규칙:

- Production은 단일 환경. Custom domain은 보류 (도메인 비용 X).
- Preview는 PR commit 별 자동 생성. Fork PR preview는 **차단 정책** (sec/preview-allowlist-policy.md).
- Phase 1은 Production / Preview 모두 같은 ACA endpoint 호출 — Phase 2에서 dev ACA 분리 결정.

## 2. GitHub 연동

세부 절차는 `github-integration-setup.md` 참고. 요약:

1. https://vercel.com/new → GitHub repo `lebind12/TourDoum` 선택
2. **Root Directory** = `frontend/`
3. Framework Preset = Vite (자동 감지)
4. Build Command / Output Directory = `vercel.json`이 박제 (자동 인식)
5. Environment Variables 추가 (`env-mapping.md` 참고)
6. Production Branch = `develop`

OAuth 기반 GitHub App 자동 연동 권장 (학습 단계 단순). Federated Credential 또는 GitHub Actions deploy hook은 Phase 2 보류.

## 3. 박제 산출물

```text
frontend/
└── vercel.json                       ← Vite build + SPA rewrite + security headers + cache-control

infra/vercel/
├── README.md                         ← 본 문서
├── github-integration-setup.md       ← 사용자 액션 단계별 절차
├── env-mapping.md                    ← 환경변수 명세 (Vercel side)
└── preview-allowlist-policy.md       ← CORS regex + fork PR 차단 정책 (BE 인계)
```

## 4. CORS / Cookie 정책 (BE 인계)

ADR-0011 §"Cookie 정책 cross-origin 갱신":

- Cookie `SameSite=None; Secure` (cross-origin)
- Backend CORS `allowCredentials=true`
- `Access-Control-Allow-Origin` 은 `*` 금지 → regex allowlist (Vercel preview 패턴 정합)
- 자세한 BE 구현 가이드: `preview-allowlist-policy.md` §3-4

## 5. 검증 (사용자 액션 후)

```bash
# vercel.json schema (Vercel CLI 설치 시)
npx vercel build --debug                      # ./frontend 에서

# 정적 JSON 문법 (CLI 없이)
jq -e . frontend/vercel.json

# preview URL 패턴 확인 (PR 생성 시 GitHub Checks)
# 패턴: tourdoum-<sha>-<team-slug>.vercel.app
```

## 6. 운영 메모

- **Vercel Free tier 한도**: Bandwidth 100GB/월, Build 6000분/월. 학습 단계 충분.
- **Cold start**: Vercel Edge Functions 사용 X (정적 SPA만). API call은 ACA cold start (`minReplicas=0` → 시연 직전 1로 toggle).
- **Preview 자동 expiry**: Vercel은 PR close 시 preview 유지 (재open 가능). 무한 누적 X (Free tier 한도 내).
- **Custom domain**: 보류. 필요 시 `tourdoum.dev` 등 구매 → Vercel domain 추가.

## 7. 금지

- Vercel API token / `VERCEL_TOKEN` commit X (GitHub Actions 호출 시 GitHub Secrets로만)
- Custom domain 박제 X (도메인 비용 발생)
- Vercel KV / Postgres / Blob 사용 X (ACA + MySQL 정책 유지)
- Edge Functions / Middleware는 Phase 2 (geo-routing 등 필요 시)

## 8. 다음 dispatch 후보

- **INFRA-AZ-2**: GitHub Actions OIDC + Federated Credential + ACR build/push + ACA revision deploy. ACA endpoint 결정 후 본 문서 §1 production URL 갱신.
- **BE-CORS-VE1**: BE에서 `preview-allowlist-policy.md` §3 정규식 read → Spring `WebMvcConfigurer` allowlist 동적 주입. Fork PR 차단 enforcement.
- **INFRA-AZ-3**: scale-lab AKS (별 task, Frontend 영향 X).
