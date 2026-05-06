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

# 3. 서버 실행 (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

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

### 테스트 환경 전략

| 테스트 종류 | DB | Flyway | ddl-auto |
|---|---|---|---|
| `@DataJpaTest` / `@SpringBootTest` 슬라이스 | H2 인메모리 | **비활성** | `create-drop` |
| 통합 테스트 (Testcontainers MySQL) | MySQL 8.4 | 비활성 (현행) | `update` |
| 실제 운영/개발 서버 | MySQL 8 | **활성** | `validate` |

## 코드 품질

```bash
# Spotless 포맷 자동 적용
./mvnw spotless:apply

# Spotless 검사만
./mvnw spotless:check

# JaCoCo 커버리지 리포트 (target/site/jacoco/index.html)
./mvnw test jacoco:report
```
