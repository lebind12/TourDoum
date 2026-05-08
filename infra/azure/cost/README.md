# Azure Cost Management — Budgets

ADR-0013 결정 (14) Cost guardrail. Phase 1은 **알림 only** (자동 차단 X).

## 파일

- `budget-prod-app.json` — `rg-tourdoum-prod-app` scope, $50 soft cap.
- `budget-scale-lab.json` — `rg-tourdoum-scale-lab` scope, $30 soft cap (단기 실험 후 RG 삭제 권고).
- `budget-subscription-hardstop.json` — subscription scope, $80 hard stop.

## 적용 (사용자 명시 승인 후)

> 본 worktree 박제 시점에서는 **`az deployment ... what-if` 만** 권고. 실제 deployment는 ADR-0013 Phase 2 INFRA-AZ-1 dispatch에서 architect 승인 후.

```bash
# subscription scope hard-stop (먼저)
az deployment sub create \
  --location koreacentral \
  --template-file budget-subscription-hardstop.json \
  --what-if

# RG-level (RG 생성 후)
az deployment group what-if \
  --resource-group rg-tourdoum-prod-app \
  --template-file budget-prod-app.json

az deployment group what-if \
  --resource-group rg-tourdoum-scale-lab \
  --template-file budget-scale-lab.json
```

## Phase 2 메모

- Action Group + Logic App webhook → `kill-scale-lab.sh` / `kill-prod-app.sh` 자동 호출 chain.
- 현재는 이메일 알림만; 50/80/100% Actual + 100% Forecasted thresholds.
- 80% Actual 도달 시 사용자가 수동으로 `infra/azure/scripts/kill-*.sh` 실행하는 것이 Phase 1 운영 정책.
