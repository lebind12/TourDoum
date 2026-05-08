# prod-lite Bicep modules

ADR-0013 Phase 2 INFRA-AZ-1. **실 Azure 리소스 생성 X** — `az bicep build` lint + `az deployment sub what-if`만 (사용자 승인 후).

Frontend = Vercel (ADR-0013 §8). 본 디렉터리는 Static Web Apps 박제 X.

## 모듈

| 파일 | 종류 | scope | 비용 (ko-central, ~) |
|---|---|---|---|
| `acr.bicep` | ACR Basic, admin disabled | prod-app RG | $5/월 |
| `log-analytics.bicep` | LA workspace, retention 30d, dailyQuotaGb=1 | prod-app RG | quota 내 무료 |
| `appinsights.bicep` | AI workspace-based, sampling 100% | prod-app RG | LA 흡수 |
| `container-apps-env.bicep` | ACA managed env, Consumption profile | prod-app RG | 환경 자체 무료 |
| `container-app.bicep` | Spring Boot ACA (min=0/max=2, 0.5 vCPU/1Gi) | prod-app RG | 평시 ~$0 (cold start) |
| `mysql.bicep` | MySQL Flexible B1ms, 32 GiB, 7d backup | prod-data RG | Free 12개월 |
| `prod-data-stack.bicep` | RG-scope wrapper for MySQL | prod-data RG | — |
| `prod-app-stack.bicep` | RG-scope wrapper + AcrPull RBAC + listKeys() | prod-app RG | — |

## 의존성 그래프

```
                 ┌─────────────┐
                 │ rgResources │ (sub scope, main.bicep)
                 └──────┬──────┘
                        │
        ┌───────────────┼─────────────────┐
        │                                 │
        ▼                                 ▼
  prod-data-stack                  prod-app-stack
   └─ mysql.bicep                   ├─ acr.bicep
        │                           ├─ log-analytics.bicep
        │ outputs.mysqlFqdn ────┐   ├─ appinsights.bicep ─── needs LA workspaceId
        │                       │   ├─ container-apps-env.bicep ─ needs LA customerId+sharedKey(listKeys)
        │                       │   ├─ container-app.bicep ─ needs ACA env + secrets (mysql JDBC + JWT + AI)
        │                       │   └─ AcrPull roleAssignment (ACR scope, ACA principalId)
        └───────────────────────┘
```

## 시크릿 정책

- 본 모듈/리포에 시크릿 commit **금지**.
- `mysqlAdminPassword`, `jwtPrivateKey` 는 `@secure() param` — `parameters/dev.bicepparam`은 `readEnvironmentVariable()`로 환경에서 주입.
- `dbConnectionString` 은 `prod-app-stack.bicep` 내부에서 password 파라미터를 합성 (commit 산출물에 노출 X).
- ACR pull credential = SystemAssigned MI (admin user disabled). GH Actions OIDC + Federated Credential 셋업은 INFRA-AZ-2.

## 검증

```bash
# 모듈별 lint
for f in infra/azure/bicep/modules/prod-lite/*.bicep; do
  az bicep build -f "$f" --stdout > /dev/null
done

# entrypoint
az bicep build -f infra/azure/bicep/main.bicep --stdout > /dev/null

# bicepparam compile (sentinel placeholder 허용)
az bicep build-params -f infra/azure/bicep/parameters/dev.bicepparam --stdout > /dev/null

# what-if (사용자 명시 승인 + 시크릿 ENV 주입 후)
AZURE_MYSQL_PW=*** AZURE_JWT_PRIVATE_KEY="$(<jwt.pem)" \
az deployment sub what-if \
  -l koreacentral \
  -f infra/azure/bicep/main.bicep \
  -p infra/azure/bicep/parameters/dev.bicepparam
```

## 운영 정책

- `minReplicas=0` 평시. 시연 직전 1로 toggle (`az containerapp update --min-replicas 1`).
- MySQL `B1ms` Free 12개월 한정. 만료 후 `az mysql flexible-server stop` (7일 자동 재기동) 또는 RG 삭제 + 백업 복원.
- LA `dailyQuotaGb=1` — ingest 초과 시 쿼리 가능, 신규 ingest 차단. 비용 cap.
- AI sampling 100% — 학습 단계만. 운영 트래픽 진입 시 5~20%로 하향 권고.
- prod-data RG 삭제 스크립트 박제 X (사용자 명시 승인 + 백업 게이트, ADR-0013 §14).

## 후속 박제 후보 (별 task)

- Key Vault + secret 위탁 — `keyvault.bicep` (Phase 2)
- VNet integration + Private endpoint (Phase 2)
- Redis (Azure Managed 또는 ACA sidecar) — Phase 2 Chat WS 진입 시점
- ACA scale rule HTTP → KEDA queue trigger (RabbitMQ outbox 진입 시점)
