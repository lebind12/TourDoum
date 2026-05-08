# Vercel ↔ GitHub Integration — 사용자 액션 절차서

> 상태: 본 dispatch 박제. 실 작업은 사용자가 dashboard에서 진행. infra teammate는 절차/검증 명령만 박제.

## 사전 조건

- GitHub repo: `lebind12/TourDoum` (develop branch live)
- Vercel 계정 (GitHub OAuth로 가입 권장 — 학습 단계 단순)
- `frontend/vercel.json` 박제 (본 dispatch 산출물)

## Step 1 — Vercel project 생성

1. https://vercel.com/new
2. **Import Git Repository** → "lebind12/TourDoum" 선택 (GitHub App 권한 요청 시 repo access 허용)
3. 화면 좌측 **Configure Project**:
   - **Project Name**: `tourdoum` (전역 unique. 충돌 시 `tourdoum-frontend`)
   - **Framework Preset**: `Vite` (자동 감지)
   - **Root Directory**: `frontend` (반드시 변경 — repo root는 `backend/` + `frontend/` monorepo)
   - **Build and Output Settings**:
     - Build Command: `npm run build` (`vercel.json` override)
     - Output Directory: `dist` (`vercel.json` override)
     - Install Command: `npm ci` (`vercel.json` override)
4. **Environment Variables**: `infra/vercel/env-mapping.md` §1 표 참고. 최소 박제:
   - `VITE_API_BASE_URL` (Production / Preview / Development 각각)
5. **Deploy** 클릭

## Step 2 — Production branch 변경

기본 `main`. TourDoum 운영 branch = `develop`.

1. Project → **Settings** → **Git**
2. **Production Branch** = `develop` 로 변경 + Save

## Step 3 — Preview Deployments 정책

기본 활성화. 보강:

1. Project → **Settings** → **Git** → **Ignored Build Step** = (default)
2. Project → **Settings** → **Deployment Protection**:
   - **Vercel Authentication** = `Standard Protection` 권고 (Preview에 인증 게이트, fork PR도 같이 차단됨)
   - 또는 `Only Preview Deployments` 만 보호
3. PR fork 차단 추가 정책: `preview-allowlist-policy.md` §2 참고

## Step 4 — Domain 정책 (Phase 1)

- Default domain `tourdoum.vercel.app` (또는 `tourdoum-<team-slug>.vercel.app`) 사용
- Custom domain 보류 (도메인 비용 X)
- 향후 `tourdoum.dev` 등 구매 시: Settings → Domains → Add → DNS CNAME `cname.vercel-dns.com`

## Step 5 — 검증 (PR 1건 open)

1. develop 에서 분기, 사소한 frontend 변경 (예: README 한 줄)
2. PR open → GitHub Checks 에 **Vercel** appear → click
3. Preview URL 확인. 패턴: `tourdoum-<git-sha-prefix>-<team>.vercel.app`
4. URL 접속 → SPA 정상 로드 (Network 탭에서 `index.html` 200 + assets 200)
5. **API call 검증**: 브라우저 콘솔에서 `fetch(import.meta.env.VITE_API_BASE_URL+'/actuator/health')` — CORS 차단 시 BE allowlist 미정합 (Phase 1 BE 미배포면 무시)

## Step 6 — Production deploy 확인

1. PR merge → develop push 트리거
2. Production deploy 자동 진행
3. URL `tourdoum.vercel.app` (또는 default) 접속

## 운영 메모

- **GitHub Actions에서 Vercel 호출 X**: 학습 단계는 Vercel-GitHub OAuth 자동 deploy로 충분. `VERCEL_TOKEN` GitHub Secrets 박제 보류.
- **Build minute / Bandwidth 모니터링**: Settings → Usage. Free tier 100GB/월, 6000분/월.
- **Rollback**: Deployments 리스트에서 이전 deploy → "Promote to Production"
- **Rebuild without push**: Project → Deployments → 최신 → "Redeploy" (cache 옵션)

## 후속 액션

- BE 측 CORS allowlist 갱신 (`preview-allowlist-policy.md` §3)
- ACA endpoint URL 박제 후 `VITE_API_BASE_URL` Production 값 갱신 (INFRA-AZ-2 끝나면)
- Vercel side `VERCEL_PROJECT_ID` / `VERCEL_ORG_ID` 등은 GitHub Actions 통합 시점에만 필요 — 현재 보류
