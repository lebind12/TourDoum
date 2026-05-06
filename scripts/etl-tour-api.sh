#!/usr/bin/env bash
# etl-tour-api.sh — 한국관광공사 TourAPI 4.0 ETL 실행 스크립트
#
# 기능:
#   1. .env 에서 TOUR_API_KEY 자동 로드 (없으면 현재 셸 환경변수 사용)
#   2. Maven으로 컴파일 + TourApiSqlGeneratorMain 실행
#   3. 결과 INSERT 문을 V4__tour_api_attractions.sql 로 저장
#
# 사용:
#   scripts/etl-tour-api.sh
#   # 또는 키를 직접 주입:
#   TOUR_API_KEY=<encoding_key> scripts/etl-tour-api.sh
#
# ADR-0005: TOUR_API_KEY는 .env 파일 또는 환경변수로만 주입.
#           코드·Git·로그에 키 값 노출 금지.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
OUTPUT_FILE="$BACKEND_DIR/src/main/resources/db/migration/V4__tour_api_attractions.sql"

# .env 자동 로드 (현재 셸 환경변수가 우선)
ENV_FILE="$PROJECT_ROOT/.env"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a
  source "$ENV_FILE"
  set +a
fi

# TOUR_API_KEY 검증
if [[ -z "${TOUR_API_KEY:-}" ]]; then
  echo "✗ TOUR_API_KEY 미설정."
  echo "  방법 1: $ENV_FILE 에 TOUR_API_KEY=<인코딩 키> 추가"
  echo "  방법 2: export TOUR_API_KEY=<인코딩 키> 후 재실행"
  echo ""
  echo "  키 발급: https://www.data.go.kr/data/15101578/openapi.do"
  echo "  → '활용신청' → 인코딩 키(encoding key) 사용"
  exit 1
fi

echo "▶ TourAPI ETL 시작"
echo "  출력: $OUTPUT_FILE"
echo ""

cd "$BACKEND_DIR"

# 컴파일 (필요 시) — -q: 조용히, -B: 비인터랙티브
echo "▶ 컴파일 중..."
./mvnw -q -B compile -DskipTests

# 런타임 의존성 target/dependency/ 로 복사
echo "▶ 의존성 준비 중..."
./mvnw -q -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

# SqlGeneratorMain 실행 → V4 SQL 생성
echo "▶ API 호출 및 SQL 생성 중... (17개 지역 × 3 contentTypeId — 시간이 걸릴 수 있음)"
TOUR_API_KEY="$TOUR_API_KEY" \
  java \
    -cp "target/classes:target/dependency/*" \
    com.ssafy.tourdoum.integration.tourapi.TourApiSqlGeneratorMain \
    > "$OUTPUT_FILE"

# 키 노출 여부 사후 검증
if grep -q "$TOUR_API_KEY" "$OUTPUT_FILE" 2>/dev/null; then
  echo "✗ 보안 경고: 출력 SQL에 TOUR_API_KEY가 포함되어 있습니다. 파일을 삭제합니다."
  rm -f "$OUTPUT_FILE"
  exit 2
fi

LINE_COUNT=$(wc -l < "$OUTPUT_FILE")
echo ""
echo "✓ 완료: $OUTPUT_FILE ($LINE_COUNT 줄)"
echo ""
echo "다음 단계:"
echo "  git add $OUTPUT_FILE"
echo "  git commit -m 'feat(etl): V4 TourAPI 수집 데이터 적재'"
echo "  # develop 머지 후 Flyway가 자동 적용"
