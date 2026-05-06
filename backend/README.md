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

## 코드 품질

```bash
# Spotless 포맷 자동 적용
./mvnw spotless:apply

# Spotless 검사만
./mvnw spotless:check

# JaCoCo 커버리지 리포트 (target/site/jacoco/index.html)
./mvnw test jacoco:report
```
