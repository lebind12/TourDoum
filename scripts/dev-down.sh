#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--wipe" ]]; then
  docker compose -f infra/docker/docker-compose.yml down -v
  echo "✓ infra down + volumes wiped"
else
  docker compose -f infra/docker/docker-compose.yml stop mysql redis
  echo "✓ mysql/redis stopped (jenkins 그대로). 볼륨 유지."
fi
