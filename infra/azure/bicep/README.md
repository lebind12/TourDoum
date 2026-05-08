# Azure Bicep — IaC skeleton

ADR-0013 Phase 2 INFRA-AZ-0. 본 디렉터리에는 **RG skeleton만** 박제한다. Container Apps / MySQL / ACR / Key Vault / AKS 모듈은 후속 dispatch.

## 파일

- `main.bicep` — subscription scope, RG 3종 (`prod-app`, `prod-data`, `scale-lab`) + tags.

## 검증 (실 deployment X)

```bash
# 1) lint (subscription 없이도 가능)
az bicep build -f main.bicep --stdout > /dev/null

# 2) what-if (사용자 명시 승인 후, 실 deployment 직전에만)
az deployment sub what-if \
  -l koreacentral \
  -f main.bicep
```

## 후속 박제 후보 (INFRA-AZ-1 ~ INFRA-AZ-2)

`plan.md` §변경 후보 파일 참고. modules/ 하위에:

- `prod-lite/` — `static-web-app.bicep`, `container-apps-env.bicep`, `container-app-api.bicep`, `mysql-flexible.bicep`, `key-vault.bicep`, `log-analytics.bicep`
- `scale-lab/` — `aks.bicep`, `aks-spot-pool.bicep`

## 운영 메모

- `targetScope = 'subscription'` — RG 자체 생성을 IaC가 관리. 사용자가 수동으로 RG를 만들어두면 `existing` 키워드로 전환할 것.
- `prod-data` RG는 tag `kill-policy=manual-approval-required` — kill scripts에서 의도적으로 제외.
- region default `koreacentral`. Free Trial credit 소비 추적 용이.
