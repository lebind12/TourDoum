# GitHub Actions ↔ Azure OIDC + Federated Credential 셋업

> ADR-0013 Phase 2 INFRA-AZ-2. **OIDC 토큰 교환** — GitHub Actions 가 azure secret token 없이 Azure resource 에 인증. App Registration + Federated Credential 등록만 사용자 액션, 이후 자동.

## 사전 조건

- Azure subscription (Free Trial OK), tenant id 확인 (`az account show`)
- GitHub repo: `lebind12/TourDoum`
- Azure RG 3종 박제 (`infra/azure/bicep/main.bicep`, INFRA-AZ-0)

## Step 1 — App Registration 생성

```bash
# (사용자 dashboard 또는 az CLI)
APP_NAME="github-oidc-tourdoum"
APP_ID=$(az ad app create --display-name "$APP_NAME" --query appId -o tsv)
echo "AZURE_CLIENT_ID=${APP_ID}"

# Service Principal 생성 (RBAC 부여 대상)
SP_OBJECT_ID=$(az ad sp create --id "$APP_ID" --query id -o tsv)
echo "SP object id: ${SP_OBJECT_ID}"
```

## Step 2 — Federated Credential 등록

GitHub OIDC issuer = `https://token.actions.githubusercontent.com`. Subject 는 trigger 별:

| trigger | subject |
|---|---|
| develop branch push | `repo:lebind12/TourDoum:ref:refs/heads/develop` |
| main branch push | `repo:lebind12/TourDoum:ref:refs/heads/main` |
| pull request | `repo:lebind12/TourDoum:pull_request` |
| environment | `repo:lebind12/TourDoum:environment:prod-lite` |

권장: **develop branch + workflow_dispatch (environment-gated)** 만.

```bash
# 1) develop branch credential
az ad app federated-credential create \
  --id "$APP_ID" \
  --parameters '{
    "name": "github-develop",
    "issuer": "https://token.actions.githubusercontent.com",
    "subject": "repo:lebind12/TourDoum:ref:refs/heads/develop",
    "audiences": ["api://AzureADTokenExchange"]
  }'

# 2) workflow_dispatch / environment 게이트 (선택)
az ad app federated-credential create \
  --id "$APP_ID" \
  --parameters '{
    "name": "github-env-prod-lite",
    "issuer": "https://token.actions.githubusercontent.com",
    "subject": "repo:lebind12/TourDoum:environment:prod-lite",
    "audiences": ["api://AzureADTokenExchange"]
  }'
```

GitHub Actions 측 환경 보호: repo Settings → Environments → `prod-lite` 생성 → required reviewers 본인.

## Step 3 — RBAC 권한

최소 권한 원칙. RG 단위로 부여 (subscription level X).

```bash
SUB_ID=$(az account show --query id -o tsv)

# 3-a) ACR push
ACR_ID=$(az acr show --name acrtourdoumprodapp --query id -o tsv)
az role assignment create \
  --assignee "$APP_ID" \
  --role AcrPush \
  --scope "$ACR_ID"

# 3-b) prod-app RG Container App 갱신 권한
az role assignment create \
  --assignee "$APP_ID" \
  --role "Container Apps Contributor" \
  --scope "/subscriptions/$SUB_ID/resourceGroups/rg-tourdoum-prod-app"

# 3-c) scale-lab RG (kill-switch workflow 용) — 단기 실험 RG, Contributor OK
az role assignment create \
  --assignee "$APP_ID" \
  --role Contributor \
  --scope "/subscriptions/$SUB_ID/resourceGroups/rg-tourdoum-scale-lab"

# 3-d) prod-data RG는 RBAC 부여 X (본 OIDC App에 prod-data 권한 차단)
```

## Step 4 — GitHub repo secrets

OIDC 토큰 교환 자체는 secret 토큰을 사용하지 않으므로 아래 3개는 **공개 설정값** (secret 보안성 X). 그래도 GitHub 관행상 secrets 에 등록한다 (값 변경/회전 용이).

```text
AZURE_CLIENT_ID         App Registration appId (Step 1)
AZURE_TENANT_ID         az account show --query tenantId
AZURE_SUBSCRIPTION_ID   az account show --query id
```

repo Settings → Secrets and variables → Actions → New repository secret. 3개 모두 등록.

## Step 5 — workflow 검증

1. `azure-acr-build-push.yml` workflow_dispatch trigger
   - Actions tab → 본 workflow → Run workflow → tag 입력 (또는 default)
   - `azure/login@v2` step 통과 = OIDC 정상
   - Build + push 성공 시 ACR portal 에 image tag 노출
2. `azure-aca-deploy.yml` workflow_dispatch trigger
   - tag 입력 → ACA revision update
   - ACA portal Revisions 탭에서 신규 revision Provisioned 확인
3. `azure-cleanup.yml` workflow_dispatch trigger
   - target=scale-lab + confirm=`yes` 입력
   - scale-lab RG 미생성 시 az group delete 가 NotFound 로 실패 → 정상 (skeleton 단계)

## 트러블슈팅

- `AADSTS70021: No matching federated identity record found` — Step 2 subject 가 trigger 와 정확히 일치하는지 확인. branch 이름 / PR / environment 분리.
- `Authorization Failed` — Step 3 RBAC 누락. RG/ACR scope 확인.
- `ACR login failed` — `AcrPush` 또는 `AcrPull` 미부여 또는 ACR firewall (Phase 1 미설정).
- `revision provisioning failed` — image tag 가 ACR 에 없거나, ACA SystemAssigned MI 가 ACR `AcrPull` 미보유 (Bicep `prod-app-stack.bicep` 의 RBAC 모듈 미적용).

## 보안 정책

- App Registration 은 **본 repo 전용**. 다른 repo 추가 시 Federated Credential 별도 등록.
- Subject 는 와일드카드 사용 X (`repo:owner/repo:*` 금지). branch/PR/environment 별 명시.
- prod-data RG 권한은 **부여 X**. 별 App Registration 또는 사용자 직접 az CLI 로만.
- secret 회전: AZURE_CLIENT_ID 변경 시 GitHub secret 재등록 + Federated Credential 재생성.
- `azure-cleanup.yml` 은 `confirm=yes` 만 통과. 향후 environment 보호 게이트 추가 권고 (required reviewer).

## 후속

- INFRA-AZ-3 (scale-lab AKS) 진입 시 `azure-aks-deploy.yml` 추가 + AKS RG RBAC.
- canary 배포 (traffic split) 별 task — `azure-aca-deploy.yml` 갱신.
- Vercel ↔ ACA cross-origin allowlist 갱신 (`infra/vercel/preview-allowlist-policy.md` 참조, BE-CORS-VE1).
