#!/usr/bin/env bash
# kill-prod-app.sh — rg-tourdoum-prod-app 내 모든 Container Apps를 min/max-replicas=0 으로 scale-down.
# RG 자체를 삭제하지 않음 (revision 이력/이미지 보존). RG 삭제는 별도 스크립트.
#
# Usage:
#   DRY_RUN=1 ./kill-prod-app.sh                                  # default
#   AZURE_EXPECTED_SUBSCRIPTION_ID=<id> DRY_RUN=0 ./kill-prod-app.sh

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib.sh
source "${SCRIPT_DIR}/_lib.sh"

RG="${RG:-rg-tourdoum-prod-app}"

log "scaling all containerapps in ${RG} to min=0/max=0"
guard_subscription

if [[ "${DRY_RUN}" == "1" ]]; then
  log "DRY_RUN: would list containerapps in ${RG} via 'az containerapp list -g ${RG} --query [].name -o tsv'"
  log "DRY_RUN: for each app: az containerapp update -g ${RG} -n <app> --min-replicas 0 --max-replicas 0"
  exit 0
fi

apps="$(az containerapp list -g "${RG}" --query "[].name" -o tsv || true)"
if [[ -z "${apps}" ]]; then
  log "no containerapps in ${RG} — noop."
  exit 0
fi

while IFS= read -r app; do
  [[ -z "${app}" ]] && continue
  log "scaling ${app} → 0/0"
  run az containerapp update -g "${RG}" -n "${app}" --min-replicas 0 --max-replicas 0
done <<< "${apps}"

log "done."
