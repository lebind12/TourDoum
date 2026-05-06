# ADR-0005: 한국관광공사 TourAPI 4.0 통합

| 항목 | 내용 |
|------|------|
| 상태 | **Accepted** |
| 날짜 | 2026-05-06 |
| 결정자 | Architect |
| 관련 ADR | ADR-0002(spatial-index), ADR-0004(db-migration) |

---

## 문제

attractions 테이블 시드(V3)는 수동 입력 50건으로, 실제 서비스에 부족하다.
한국관광공사 TourAPI 4.0 (KorService2)으로 전국 여행지 데이터를 수집해 V4 마이그레이션으로 적재한다.

---

## 결정

### 1. 데이터 소스
한국관광공사 TourAPI 4.0 (KorService2) 채택.
- Base URL: `https://apis.data.go.kr/B551011/KorService2`
- 인증: `serviceKey` 쿼리 파라미터 (공공데이터포털 표준 encoding 키)

### 2. 호출 클라이언트
Spring 6.1+에 내장된 `RestClient` 사용.
- `spring-boot-starter-web`에 포함 — 추가 의존성 불필요.
- 비동기/배치 도입은 후속 ADR로 결정.

### 3. ETL 트리거
- **CLI**: `scripts/etl-tour-api.sh` 한 줄로 V4 SQL 생성.
- **별도 main 클래스**: `TourApiSqlGeneratorMain` — Spring Boot 컨텍스트 미기동, DB 연결 불필요.
- 운영: cron 또는 수동 실행. 스케줄러 도입은 후속.

### 4. 적재 방식
- V4 마이그레이션 SQL 자동 생성 (INSERT 문). 재현 가능.
- 매번 갱신 시 V{N+1}로 새 마이그레이션 추가 (ADR-0004 정책 준수).

### 5. 카테고리 매핑
| contentTypeId | 1차 매핑 | 비고 |
|---|---|---|
| 12 (관광지) | `OTHER` | 1차 구현 |
| 14 (문화시설) | `HISTORY` | 선택적 추가 |
| 28 (레포츠) | `ACTIVITY` | 선택적 추가 |

**미해결**: `detailCommon2` API의 `cat1/cat2/cat3`까지 파싱해 NATURE/HISTORY/ACTIVITY를 세분화하는 작업은 후속 worktree에 위임.

### 6. 중복 정책
- `tour_api_id` 컬럼에 UNIQUE 제약 추가 (V5 마이그레이션).
- NULL 다중 허용 → V3 시드 50건(모두 NULL)에 영향 없음.
- V4 INSERT에서는 `tour_api_id`가 이미 존재하면 SKIP (로그 기록).
- H2 호환을 위해 `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` 사용 안 함.
  대신 실행 전 중복 체크 후 미삽입 (SQL Generator 레벨에서 중복 제거).

### 7. 실패/재시도
- 페이지 단위 실패 시 해당 페이지 스킵, 다음 페이지 진행.
- 전체 실패(키 미발급 / 네트워크 차단) 시 V4 SQL 미생성.

### 8. 키 노출 방지
- `TOUR_API_KEY`는 `.env` 파일 또는 환경변수로만 주입.
- 코드·로그·SQL 파일 어디에도 키 값 출력 금지.
- `.env`는 `.gitignore`에 포함.

---

## 대안 고려

| 대안 | 탈락 이유 |
|---|---|
| WebClient (Reactor) | 추가 의존성(spring-boot-starter-webflux) 필요, 동기 ETL에 과도 |
| Batch 스케줄러 | 1차 요구 범위 초과, 후속 ADR로 |
| 수동 SQL 작성 | 데이터 품질/규모 한계 |

---

## 결과

- `integration/tourapi/` 패키지: Client / Mapper / Service / Properties / SqlGeneratorMain.
- `scripts/etl-tour-api.sh`: `.env` 자동 로드, V4 SQL 생성.
- `backend/README.md`에 TourAPI 데이터 갱신 섹션 추가.
- V5 마이그레이션: `uk_attractions_tour_api_id` UNIQUE 제약.
