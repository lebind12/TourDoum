#!/usr/bin/env bash
# Runs the auth-flow Playwright e2e against a real local backend.
#
# This script starts MySQL/Redis through dev-up.sh, starts Spring Boot in the
# background, waits for /api/health, then lets Playwright start the Vite server.
# It stops only the backend process it started; infra containers are left running
# for local inspection and can be stopped with scripts/dev-down.sh.

set -euo pipefail
cd "$(dirname "$0")/.."

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "✗ required command not found: $1" >&2
    exit 1
  }
}

require_cmd curl
require_cmd docker

LOG_DIR="${TMPDIR:-/tmp}/tourdoum-e2e"
mkdir -p "$LOG_DIR"
BACKEND_LOG="$LOG_DIR/backend-auth-flow.log"
BACKEND_PID=""

cleanup() {
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
    wait "$BACKEND_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "==> starting MySQL/Redis"
./scripts/dev-up.sh

echo "==> starting backend (log: $BACKEND_LOG)"
(
  cd backend
  ./mvnw -q spring-boot:run
) >"$BACKEND_LOG" 2>&1 &
BACKEND_PID="$!"

echo "==> waiting for backend health"
for _ in {1..90}; do
  if curl -fsS "http://localhost:8080/api/health" >/dev/null 2>&1; then
    echo "✓ backend healthy"
    break
  fi

  if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    echo "✗ backend exited before becoming healthy" >&2
    tail -n 80 "$BACKEND_LOG" >&2 || true
    exit 1
  fi

  sleep 2
done

if ! curl -fsS "http://localhost:8080/api/health" >/dev/null 2>&1; then
  echo "✗ backend health timeout" >&2
  tail -n 80 "$BACKEND_LOG" >&2 || true
  exit 1
fi

echo "==> running Playwright auth flow"
(
  cd frontend
  VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:8080}" \
    E2E_BACKEND=1 \
    npm run test:e2e -- e2e/auth-flow.spec.ts
)
