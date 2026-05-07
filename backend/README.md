# TourDoum Backend

Spring Boot 3.3.x + JDK 21 + Maven 백엔드.

## 사전 요구 사항

| 도구 | 버전 |
|---|---|
| JDK | 21 (LTS) |
| Maven Wrapper | 3.9.9 (포함됨) |
| Docker | 최신 — Testcontainers 통합 테스트용 |

Maven은 프로젝트 내 `./mvnw` 를 사용한다. 별도 설치 불필요.
단, mvnw가 없는 환경(드물게)이라면 로컬 Maven 3.9+ 필요.

## 빠른 시작

```bash
# 1. 환경변수 복사
cp .env.example .env
# .env 에서 필요한 값 수정

# 2. 빌드 + 단위/슬라이스 테스트
./mvnw -B verify

# 3. 서버 실행
./mvnw spring-boot:run

# 4. 헬스 확인
curl http://localhost:8080/api/health
# {"status":"UP","app":"tourdoum"}
```

## 테스트 실행

```bash
# 단위 + 슬라이스 테스트만 (빠름, Docker 불필요)
./mvnw test

# 통합 테스트 포함 (Docker 필요)
./mvnw verify -DskipITs=false
# 또는
./mvnw verify -Pit
```

## 로컬 인프라 (MySQL + Redis)

워크스페이스 루트의 docker-compose를 사용한다.

```bash
# 20-spec-tourdoum/ 기준
cd ../infra/docker
docker compose up -d mysql redis
```

docker-compose 상세는 `infra/docker/docker-compose.yml` 참고.

## 주요 엔드포인트

| Method | URI | 설명 |
|---|---|---|
| GET | `/api/health` | 학습용 커스텀 헬스체크 |
| GET | `/actuator/health` | Spring Actuator 헬스체크 |

## 패키지 구조

```
com.ssafy.tourdoum
├── TourdoumApplication.java    # 메인
└── health/
    └── HealthController.java   # GET /api/health
```

도메인 패키지(attraction, accommodation, member 등)는 후속 브랜치에서 추가된다.

## DB 마이그레이션 (Flyway)

ADR-0004(2026-05-06) 기준. Flyway가 `src/main/resources/db/migration/` 아래의 SQL 파일로 스키마를 관리한다.

### 파일 명명 규칙

```
V{버전}__{설명}.sql
예) V1__init.sql   V2__attraction.sql   V3__add_thumbnail_url.sql
```

- 대문자 `V`, 정수 버전, 이중 언더스코어(`__`), snake_case 설명, `.sql` 확장자.
- 버전 번호는 단조 증가. 중간 빈 번호는 허용하지 않는 것을 권장.

### 새 마이그레이션 추가 절차

1. `src/main/resources/db/migration/V{다음번호}__{설명}.sql` 파일 작성 (MySQL 8 문법).
2. `./mvnw -DskipITs verify` 로 로컬 빌드 통과 확인 (단위/슬라이스는 H2+create-drop, Flyway 비활성).
3. 로컬 MySQL에서 앱을 기동해 Flyway가 해당 버전을 정상 적용하는지 로그 확인 후 커밋.

### 기존 마이그레이션 수정 금지

이미 커밋된 V{n}__.sql 파일은 **절대 수정하지 않는다.**
Flyway는 파일 체크섬을 검증하며, 불일치 시 애플리케이션 기동을 거부한다.

변경이 필요하다면:
- 새 파일 `V{n+1}__compensate_xxx.sql`을 작성해 보상(역변환) 마이그레이션을 적용한다.
- 개발 중 깨진 상태 복구: `./mvnw flyway:repair` (체크섬 재계산)

### TourAPI 데이터 갱신

V4(`V4__tour_api_attractions.sql`)에 한국관광공사 TourAPI 4.0 (KorService2) 수집 스냅샷 13,155건이 박제되어 있다. 갱신은 **새 V{N+1} 마이그레이션**으로 보강한다.

#### 1) data.go.kr 활용신청

1. https://www.data.go.kr/ 로그인 → 마이페이지 → 활용신청 현황
2. **"한국관광공사_국문 관광정보 서비스_GW"**(ID 15101578) 활용신청
3. 자동승인 후 인코딩 키 발급
4. 워크스페이스 루트 `.env` 의 `TOUR_API_KEY=` 에 주입 (이미 있다면 갱신)

#### 2) 신규 데이터 수집 스냅샷 작성

본 V4 박제 시 사용한 명령(향후 동일 패턴):

```bash
# 1. 라이브 ETL 실행 (별도 worktree에서 TourApiClient 호출 — ADR-0006 후속)
# 2. MySQL에 적재 후, 새 V{N+1} 작성을 위한 스냅샷 dump:
WT_OUT=src/main/resources/db/migration/V{N+1}__tour_api_refresh.sql
docker exec tourdoum-mysql mysqldump \
  --no-create-info --skip-extended-insert --complete-insert \
  --default-character-set=utf8mb4 \
  -u root -prootpw \
  --where="tour_api_id IS NOT NULL AND tour_api_id NOT IN (SELECT tour_api_id FROM ... 기존 V4 셋)" \
  tourdoum attractions \
  | grep '^INSERT' >> $WT_OUT
```

기존 데이터와 충돌은 V5의 `tour_api_id UNIQUE` 제약이 막는다. 갱신 마이그레이션은 `INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE` 형태 권장.

#### 3) 로컬 DB가 이미 깨진 상태일 때

체크섬 불일치 등으로 Flyway 기동 거부 시:

```bash
# 깨진 메타데이터 정리 (성공 row만 유지, 체크섬 재계산)
./mvnw flyway:repair

# 또는 dev 데이터 통째로 리셋 (주의)
docker compose -f ../infra/docker/docker-compose.yml down -v
docker compose -f ../infra/docker/docker-compose.yml up -d mysql redis
./mvnw spring-boot:run   # Flyway가 V1~V5 처음부터 적용
```

### 테스트 환경 전략

| 테스트 종류 | DB | Flyway | ddl-auto |
|---|---|---|---|
| `@DataJpaTest` / `@SpringBootTest` 슬라이스 | H2 인메모리 | **비활성** | `create-drop` |
| 통합 테스트 (Testcontainers MySQL) | MySQL 8.4 | 비활성 (현행) | `update` |
| 실제 운영/개발 서버 | MySQL 8 | **활성** | `validate` |

## Swagger / OpenAPI 사용법

springdoc-openapi 2.6.0이 내장되어 있다. 서버 실행 후 아래 URL로 접근한다.

### URL

| 용도 | URL |
|---|---|
| Swagger UI (브라우저 인터랙티브) | http://localhost:8080/swagger-ui.html |
| OpenAPI 3.0 JSON spec | http://localhost:8080/v3/api-docs |

### 빠른 검증

```bash
# 서버 실행 후
curl http://localhost:8080/v3/api-docs | jq '.info'
# → {"title":"TourDoum API","version":"0.1.0",...}

curl -o /dev/null -s -w "%{http_code}" http://localhost:8080/swagger-ui.html
# → 200
```

### 로그인 후 보호 엔드포인트 테스트

Swagger UI는 브라우저에서 직접 실행 시 SESSION 쿠키가 자동 전송된다:

1. `POST /api/auth/login` 실행 → 브라우저에 SESSION 쿠키 자동 저장
2. `GET /api/me` 같은 보호 엔드포인트 실행 → 쿠키 자동 포함

> **주의**: `curl` 등 별도 클라이언트에서 테스트 시 `-b "SESSION=<값>"` 옵션 필요.

### 운영 환경 비활성화

prod profile에서 Swagger를 닫으려면 `application-prod.yml`에 추가:

```yaml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

### 새 컨트롤러에 어노테이션 추가 가이드

```java
@Tag(name = "Domain", description = "도메인 설명")
@RestController
public class DomainController {

  @Operation(summary = "엔드포인트 요약", description = "상세 설명")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "404", description = "리소스 없음")
  })
  @SecurityRequirement(name = "SESSION")  // 인증 필요 엔드포인트에만 추가
  @GetMapping("/api/domain/{id}")
  public DomainResponse getById(@PathVariable Long id) { ... }
}
```

후속 도메인(즐겨찾기/숙박/채팅) 컨트롤러에 동일 패턴 적용. 자세한 결정 사항은 `docs/adr/0008-swagger-openapi.md` 참고.

---

## 코드 품질

```bash
# Spotless 포맷 자동 적용
./mvnw spotless:apply

# Spotless 검사만
./mvnw spotless:check

# JaCoCo 커버리지 리포트 (target/site/jacoco/index.html)
./mvnw test jacoco:report
```
