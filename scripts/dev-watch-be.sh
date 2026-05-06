#!/usr/bin/env bash
# Backend 소스를 감시하다가 변경되면 mvnw compile을 트리거.
# spring-boot:run이 떠 있으면 DevTools가 1~2초 안에 컨텍스트만 재시작.
#
# 동작 원리:
#   find로 감시 대상 파일 목록 출력 → entr가 stdin에 들어온 파일들을 워치 → 변경 감지 시 -c(콘솔 클리어) 후 명령 실행
#
# 의존: entr (`brew install entr`). 미설치면 안내 출력 후 종료.

set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v entr >/dev/null 2>&1; then
  cat <<EOF
✗ entr가 설치되어 있지 않습니다.

설치:  brew install entr

대안:
  - IntelliJ를 쓰면 'Build project automatically' + 'Allow auto-make to start...' 두 옵션을 켜면 IDE가 자동 컴파일.
  - Gradle 스타일 continuous build가 필요하면 ADR-0005 분리하여 Gradle 마이그레이션 검토.
EOF
  exit 1
fi

echo "watching backend/src/main + pom.xml ..."
echo "  → 변경 감지 시 ./mvnw -q compile 실행 (DevTools가 컨텍스트 재시작)"
echo

# pom.xml을 함께 워치해서, 의존성 추가 시 사용자에게 경고만 띄움 (자동 재기동은 못 함)
find backend/src/main backend/pom.xml -type f \( -name '*.java' -o -name '*.yml' -o -name '*.yaml' -o -name 'pom.xml' \) 2>/dev/null \
  | entr -c -s '
      changed=$0
      if [[ "$changed" == */pom.xml ]]; then
        echo "⚠ pom.xml이 바뀌었습니다 — 의존성/플러그인 변경은 DevTools로 못 잡습니다."
        echo "   spring-boot:run을 종료하고 다시 띄워주세요."
      fi
      cd backend && ./mvnw -q compile && echo "✓ compiled at $(date +%H:%M:%S)"
    '
