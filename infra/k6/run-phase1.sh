#!/usr/bin/env bash
# QA-K6-1 Phase 1 saturation runner.
#
# 1) DB outbox/reservations baseline 캡처
# 2) k6 run --summary-export → JSON 결과 파일 저장
# 3) DB outbox 상태 변화 측정 (drain rate)
# 4) docs/notes/2026-05-08-qa-k6-1-phase1-baseline.md 추가 입력 데이터 출력
#
# 전제: BE 30080 healthy, MySQL container `tourdoum-mysql` 실행 중.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RESULTS_DIR="${ROOT}/infra/k6/results"
mkdir -p "${RESULTS_DIR}"

TS=$(date +%Y%m%dT%H%M%S)
SUMMARY="${RESULTS_DIR}/phase1-${TS}-summary.json"
DB_BEFORE="${RESULTS_DIR}/phase1-${TS}-db-before.txt"
DB_AFTER="${RESULTS_DIR}/phase1-${TS}-db-after.txt"
DB_FINAL="${RESULTS_DIR}/phase1-${TS}-db-final.txt"

mysql_q() {
  docker exec tourdoum-mysql mysql -utourdoum -ptourdoum -D tourdoum -e "$1" 2>/dev/null
}

echo "[run] BE health…"
curl -sf http://localhost:30080/api/health > /dev/null || { echo "BE down — abort"; exit 1; }

echo "[run] DB baseline → ${DB_BEFORE}"
mysql_q "SELECT (SELECT COUNT(*) FROM reservations) reservations,
                (SELECT COUNT(*) FROM outbox) outbox_total,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='PENDING') outbox_pending,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='CLAIMED') outbox_claimed,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='DONE') outbox_done,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='FAILED') outbox_failed,
                (SELECT COUNT(*) FROM outbox_dead_letter) outbox_dlq" \
  | tee "${DB_BEFORE}"

echo "[run] k6 saturation start (~5분)…"
k6 run \
  --summary-export "${SUMMARY}" \
  --summary-trend-stats "avg,min,med,p(95),p(99),max" \
  "${ROOT}/infra/k6/scripts/phase1-saturation.js"

echo "[run] DB immediately after k6 → ${DB_AFTER}"
mysql_q "SELECT (SELECT COUNT(*) FROM reservations) reservations,
                (SELECT COUNT(*) FROM outbox) outbox_total,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='PENDING') outbox_pending,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='CLAIMED') outbox_claimed,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='DONE') outbox_done,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='FAILED') outbox_failed,
                (SELECT COUNT(*) FROM outbox_dead_letter) outbox_dlq" \
  | tee "${DB_AFTER}"

echo "[run] 30s 대기 (publisher drain)…"
sleep 30

echo "[run] DB final → ${DB_FINAL}"
mysql_q "SELECT (SELECT COUNT(*) FROM reservations) reservations,
                (SELECT COUNT(*) FROM outbox) outbox_total,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='PENDING') outbox_pending,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='CLAIMED') outbox_claimed,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='DONE') outbox_done,
                (SELECT COUNT(*) FROM outbox WHERE claim_state='FAILED') outbox_failed,
                (SELECT COUNT(*) FROM outbox_dead_letter) outbox_dlq" \
  | tee "${DB_FINAL}"

echo
echo "[run] DONE — summary=${SUMMARY}"
echo "[run] db before=${DB_BEFORE} after=${DB_AFTER} final=${DB_FINAL}"
