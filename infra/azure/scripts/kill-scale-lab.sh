#!/usr/bin/env bash
# kill-scale-lab.sh — rg-tourdoum-scale-lab RG 전체 삭제.
# ADR-0013 Phase 2 §14 — scale-lab은 단기 실험 후 항상 삭제 대상.
#
# Usage:
#   DRY_RUN=1 ./kill-scale-lab.sh                                    # default
#   AZURE_EXPECTED_SUBSCRIPTION_ID=<id> DRY_RUN=0 ./kill-scale-lab.sh
#   FORCE=1 DRY_RUN=0 ... ./kill-scale-lab.sh                        # non-TTY

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib.sh
source "${SCRIPT_DIR}/_lib.sh"

RG="${RG:-rg-tourdoum-scale-lab}"

log "target: az group delete --name ${RG} --yes --no-wait"
guard_subscription
confirm_tty "DELETE Resource Group '${RG}' (irreversible)? Type 'yes' to proceed: "
run az group delete --name "${RG}" --yes --no-wait
log "done."
