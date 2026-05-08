#!/usr/bin/env bash
# 공통 helper. ADR-0013 Phase 2 INFRA-AZ-0.
# 모든 kill switch 스크립트는 이 파일을 source 한다.

set -euo pipefail

# DRY_RUN=1 (default) — 실제 az CLI 호출 대신 명령만 출력.
DRY_RUN="${DRY_RUN:-1}"

# AZURE_EXPECTED_SUBSCRIPTION_ID — 실 실행 시 사용자가 지정해야 할 가드.
# 미지정 시 dry-run 강제.
AZURE_EXPECTED_SUBSCRIPTION_ID="${AZURE_EXPECTED_SUBSCRIPTION_ID:-}"

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%SZ)" "$*"
}

run() {
  if [[ "${DRY_RUN}" == "1" ]]; then
    log "DRY_RUN: $*"
  else
    log "RUN: $*"
    "$@"
  fi
}

guard_subscription() {
  if [[ "${DRY_RUN}" == "1" ]]; then
    log "guard_subscription: skipped (DRY_RUN=1)"
    return 0
  fi
  if [[ -z "${AZURE_EXPECTED_SUBSCRIPTION_ID}" ]]; then
    log "ERROR: AZURE_EXPECTED_SUBSCRIPTION_ID not set. 실 실행은 expected subscription id 필수."
    exit 2
  fi
  local actual
  actual="$(az account show --query id -o tsv)"
  if [[ "${actual}" != "${AZURE_EXPECTED_SUBSCRIPTION_ID}" ]]; then
    log "ERROR: subscription mismatch. expected=${AZURE_EXPECTED_SUBSCRIPTION_ID} actual=${actual}"
    exit 3
  fi
  log "guard_subscription: ok (${actual})"
}

confirm_tty() {
  local prompt="${1:-Continue? [yes/N] }"
  if [[ "${DRY_RUN}" == "1" ]]; then return 0; fi
  if [[ ! -t 0 ]]; then
    log "ERROR: stdin not a TTY. interactive confirmation 필요. DRY_RUN=1 또는 FORCE=1 사용."
    [[ "${FORCE:-0}" == "1" ]] || exit 4
    return 0
  fi
  read -r -p "${prompt}" answer
  [[ "${answer}" == "yes" ]] || { log "aborted."; exit 5; }
}
