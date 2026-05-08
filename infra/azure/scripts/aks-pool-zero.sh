#!/usr/bin/env bash
# aks-pool-zero.sh — AKS userpool node-count=0 으로 scale-down (cluster 자체 보존).
# scale-lab AKS 비용 일시 중단용. 클러스터 완전 삭제는 kill-scale-lab.sh.
#
# Usage:
#   DRY_RUN=1 ./aks-pool-zero.sh
#   AZURE_EXPECTED_SUBSCRIPTION_ID=<id> DRY_RUN=0 ./aks-pool-zero.sh

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib.sh
source "${SCRIPT_DIR}/_lib.sh"

RG="${RG:-rg-tourdoum-scale-lab}"
CLUSTER="${CLUSTER:-aks-tourdoum-scale-lab}"
POOL="${POOL:-userpool}"

log "scaling AKS pool ${RG}/${CLUSTER}/${POOL} → 0"
guard_subscription
run az aks nodepool scale \
  --resource-group "${RG}" \
  --cluster-name "${CLUSTER}" \
  --name "${POOL}" \
  --node-count 0
log "done."
