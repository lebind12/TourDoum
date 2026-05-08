# Azure Kill Switch Scripts

ADR-0013 Phase 2 INFRA-AZ-0. **DRY_RUN=1 default**, 실 실행은 `AZURE_EXPECTED_SUBSCRIPTION_ID` 가드 + TTY 확인 필수.

## 스크립트

| 파일 | 동작 | 복구 |
|---|---|---|
| `kill-scale-lab.sh` | `rg-tourdoum-scale-lab` RG 전체 삭제 | Bicep 재배포 |
| `kill-prod-app.sh` | `rg-tourdoum-prod-app` 내 Container Apps `min/max-replicas=0` | `containerapp update --min-replicas 1 ...` |
| `aks-pool-zero.sh` | AKS userpool `node-count=0` (cluster 보존) | `nodepool scale --node-count <N>` |
| ~~`kill-prod-data-rg.sh`~~ | **박제 보류** — 사용자 명시 승인 + 백업 게이트 (ADR-0013 §14) | — |

## 공통 가드

- `set -euo pipefail`
- `DRY_RUN=1` (default) — `az` CLI 호출 대신 명령만 stdout
- `AZURE_EXPECTED_SUBSCRIPTION_ID` — 실 실행 시 필수. `az account show --query id` 와 비교, 불일치 시 exit 3
- TTY 확인 — `kill-scale-lab.sh` 에서 `yes` 입력 요구. non-TTY 환경은 `FORCE=1`

## 기본 사용

```bash
# 검증만
./kill-scale-lab.sh

# 실 실행 (사용자 명시 승인 후)
AZURE_EXPECTED_SUBSCRIPTION_ID=<sub-id> DRY_RUN=0 ./kill-scale-lab.sh
```

## shellcheck

```bash
shellcheck infra/azure/scripts/*.sh
```

## prod-data RG 처리 정책

`rg-tourdoum-prod-data`에는 MySQL Flexible Server / Key Vault / Storage 등 stateful 데이터가 들어간다. 삭제 스크립트는 **박제하지 않는다**. 비용 차단이 필요하면:

1. `infra/azure/scripts/kill-prod-app.sh` 로 app tier 차단 (DB는 유지)
2. MySQL Flexible Server `az mysql flexible-server stop` (7일 자동 재기동 주의)
3. 장기 중단 시 사용자 + architect 승인 후 수동 `az group delete rg-tourdoum-prod-data`
