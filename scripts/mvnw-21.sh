#!/usr/bin/env bash
# mvnw-21.sh — JAVA_HOME=21 자동 설정 후 backend의 mvnw 호출.
#
# 배경: jenv가 .java-version=21을 인식하지만 sub-shell(에이전트가 spawn하는 Bash)에선
# .zshrc 미로드로 jenv shim이 PATH에 안 들어가, ./mvnw가 시스템 default JDK(보통 17)로
# 실행되며 'release version 21 not supported' 오류 발생.
#
# 사용:
#   ./scripts/mvnw-21.sh -DskipITs verify
#   ./scripts/mvnw-21.sh spring-boot:run
#   ./scripts/mvnw-21.sh -B -DskipITs=false -Dtourdoum.it=true verify

set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]] || ! "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q '"21'; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "✗ JDK 21 not found. Install via 'brew install openjdk@21' or jenv." >&2
  exit 1
fi

cd "$PROJECT_ROOT/backend"
exec ./mvnw "$@"
