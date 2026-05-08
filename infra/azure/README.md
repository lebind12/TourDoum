# TourDoum Azure Infra

> 상태: ADR-0013 Phase 2 INFRA-AZ-0 — RG skeleton + Cost budget + Kill switch 박제. 실제 Azure 리소스 생성 X.
> 적용은 architect/사용자 명시 승인 후 후속 dispatch (INFRA-AZ-1, INFRA-AZ-2).

## 1. 환경 원칙

```text
prod-lite     실제 공개 서비스 데모용. 장기 운영. 저비용 우선.
scale-lab     30만 RPS 스트레스 테스트 실험실. 단기 실행 후 삭제.
```

핵심 규칙:

- 실제 서비스는 Azure Container Apps 기반.
- 30만 RPS 실험은 AKS 기반으로 분리.
- managed service는 학습/비교 목적이 분명할 때만.
- 모든 Azure 리소스는 RG 단위로 묶고 kill switch를 먼저 만든다.

## 2. Resource Group

| RG | 용도 | kill policy |
|---|---|---|
| `rg-tourdoum-prod-app` | Static Web Apps, Container Apps, ACR | kill-switch 허용 |
| `rg-tourdoum-prod-data` | MySQL Flexible, Key Vault, Storage | manual approval + 백업 |
| `rg-tourdoum-scale-lab` | AKS Free, k6, self-host data plane, observability | 단기 실험 후 삭제 |

박제: `bicep/main.bicep` (subscription scope).

## 3. prod-lite 후보

```text
Azure Static Web Apps      Vue frontend
Azure Container Apps       Spring Boot, minReplicas=0(cost) / 1(demo)
MySQL Flexible Server      Standard_B1ms 시작
Redis                      A: Azure Managed Redis  /  B: MySQL fallback
Secrets                    Phase 1: ACA secrets / Phase 2: Key Vault + MI
```

## 4. scale-lab 후보

```text
AKS Free tier              spot node pool, max node hard limit
k6-operator                30만 RPS generator
NGINX Ingress              rate limit / saturation
Spring Boot pods           N=2/4/8 scale test
Redis Cluster (self-host)  Lua token pre-confirm
MariaDB shards             accommodation_id hash
RabbitMQ                   queue/backpressure
Prometheus/Grafana         self-host (Azure Monitor managed은 Phase 2)
```

## 5. 박제된 산출물 (INFRA-AZ-0)

```text
infra/azure/
├── README.md                        ← 본 문서
├── bicep/
│   ├── main.bicep                   ← RG 3종 (subscription scope) + tags
│   └── README.md
├── cost/
│   ├── budget-prod-app.json         ← $50 alert (50/80/100% Actual + 100% Forecast)
│   ├── budget-scale-lab.json        ← $30 alert
│   ├── budget-subscription-hardstop.json  ← $80 hard stop (subscription scope)
│   └── README.md
└── scripts/
    ├── _lib.sh                      ← DRY_RUN + subscription guard + TTY confirm
    ├── kill-scale-lab.sh            ← rg-tourdoum-scale-lab 삭제
    ├── kill-prod-app.sh             ← Container Apps min/max-replicas=0
    ├── aks-pool-zero.sh             ← AKS userpool node-count=0
    └── README.md
```

박제 X (보류): `kill-prod-data-rg.sh` — stateful data 삭제는 사용자 명시 승인 + 백업 게이트.

## 6. 운영 정책

- **DRY_RUN=1 default** — 모든 kill switch는 명령 출력만, 실 실행은 `DRY_RUN=0` + `AZURE_EXPECTED_SUBSCRIPTION_ID` 가드 통과 필수.
- **시크릿 commit 금지** — Key Vault/ACR 인증/connection string은 GitHub Secrets + OIDC federated credential. 본 리포에는 박제 X.
- **what-if before deploy** — Bicep/Budget JSON 모두 `az deployment ... what-if` 후 architect 승인.
- **budget Phase 1 = 알림 only** — 자동 차단 chain (Logic App → kill scripts) 은 Phase 2.

## 7. 검증 명령

```bash
# Bicep lint
az bicep build -f infra/azure/bicep/main.bicep --stdout > /dev/null

# JSON syntax
jq -e . infra/azure/cost/*.json

# Shell syntax
for f in infra/azure/scripts/*.sh; do bash -n "$f"; done
shellcheck infra/azure/scripts/*.sh

# Subscription scope budget what-if (사용자 명시 승인 후)
az deployment sub what-if -l koreacentral \
  -f infra/azure/cost/budget-subscription-hardstop.json

# RG skeleton what-if
az deployment sub what-if -l koreacentral -f infra/azure/bicep/main.bicep
```

## 8. 다음 dispatch 후보

- **INFRA-AZ-1** — prod-lite 본격 박제 (Static Web Apps + Container Apps + MySQL Flexible + ACR + Key Vault + Log Analytics).
- **INFRA-VE-1** — Federated Credential 셋업 + GitHub Actions skeleton (`azure-acr-build-push`, `azure-aca-deploy`, `azure-swa-deploy`).
- **INFRA-AZ-2** — scale-lab AKS Bicep + Helm values (k6-operator / NGINX / Redis / MariaDB / RabbitMQ / kube-prom-stack) + kind dry-run.

## 9. Kill Switch 명령 (참조)

```bash
# scale-lab 전체 삭제
infra/azure/scripts/kill-scale-lab.sh                                    # DRY_RUN=1
AZURE_EXPECTED_SUBSCRIPTION_ID=<sub> DRY_RUN=0 \
  infra/azure/scripts/kill-scale-lab.sh

# prod app scale-down (data 보존)
AZURE_EXPECTED_SUBSCRIPTION_ID=<sub> DRY_RUN=0 \
  infra/azure/scripts/kill-prod-app.sh

# AKS pool 0
AZURE_EXPECTED_SUBSCRIPTION_ID=<sub> DRY_RUN=0 \
  infra/azure/scripts/aks-pool-zero.sh

# 위험 — prod data RG 삭제는 박제 X. 필요 시 사용자 + architect 승인 후 직접:
#   az group delete --name rg-tourdoum-prod-data --yes
```
