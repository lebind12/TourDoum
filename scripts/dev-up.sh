#!/usr/bin/env bash
# 개발용 인프라(MySQL, Redis)만 띄운다. Jenkins는 dev에서 안 띄움.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose -f infra/docker/docker-compose.yml up -d mysql redis
echo
echo "✓ infra up. health 대기..."
for svc in mysql redis; do
  until [ "$(docker inspect --format '{{.State.Health.Status}}' "tourdoum-$svc" 2>/dev/null)" = "healthy" ]; do
    printf '.'
    sleep 2
  done
  echo " $svc healthy"
done
echo
echo "다음:"
echo "  터미널 1 → cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
echo "  터미널 2 → cd frontend && npm run dev"
echo
echo "내릴 때: scripts/dev-down.sh (볼륨 유지) / scripts/dev-down.sh --wipe (볼륨 삭제)"
