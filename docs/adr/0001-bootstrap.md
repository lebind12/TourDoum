# ADR 0001 — Bootstrap (TourDoum)

- 작성일: 2026-05-06
- 상태: Accepted
- 작성자: 사용자 + Architect (Claude Opus 4.7)

## 컨텍스트

SSAFY 특화 프로젝트로 **TourDoum**(전국 여행지 + 주변 숙박 추천)을 재구현한다.
사용자는 백엔드/프론트엔드 모두 학습 중이며, **테스트 작성 경험은 없다**.
공공 API(한국관광공사 TourAPI 계열) 기반의 실제 여행지 데이터와, 가상/수집 혼합의
숙박 데이터를 MySQL 8 공간 인덱스 위에서 다룬다.

## 결정 요약

| 항목 | 값 | 근거 |
|---|---|---|
| 트랙 | 특화 (`20-spec-tourdoum/`) | CLAUDE.md §1 prefix 규칙 |
| 모듈 구성 | **모노레포** (단일 .git, `backend/` + `frontend/`) | SSAFY 특화 PJT 규모, CI 일괄 검증, 사용자 선택 |
| 언어/런타임 (BE) | **JDK 21** | LTS, virtual thread, Spring Boot 3.3+ 안정 |
| 빌드 도구 (BE) | **Maven** | 사용자 선택 (Gradle 대안 기각) |
| 프레임워크 (BE) | **Spring Boot 3.3.x** | 표준 |
| ORM | **JPA(Hibernate) + QueryDSL** | RDB 친화 도메인, 동적 쿼리 |
| DB | **MySQL 8** (공간 인덱스 `POINT` + `ST_Distance_Sphere`) | 별도 PostGIS 없이 반경 검색 충족 |
| 세션 | **Redis 7** | 폼 로그인 세션 저장소, Spring Session Data Redis |
| 프론트엔드 | **Vue 3 + Vite + TypeScript** | 2026 표준 |
| 패키지 매니저 (FE) | **npm** | 사용자 선택 |
| FE 라이브러리 | **Pinia** + **vue-router** + **Kakao Map JS SDK** | 국내 POI/길찾기 품질 |
| 인증 | 세션(Redis) 기반 폼 로그인부터, 카카오 소셜 로그인은 후속 | 학습 곡선 |
| 객체 저장 | 로컬 디렉터리(개발) + S3 호환 인터페이스 추상화(차후) | 단순화 |
| 테스트 (BE) | **JUnit5 + AssertJ + Mockito** + `@WebMvcTest`/`@DataJpaTest` + **Testcontainers**(MySQL/Redis) | 실무 표준 |
| 테스트 (FE) | **Vitest** + Vue Test Utils + **MSW** + **Playwright**(e2e 1~2 시나리오) | 실무 표준 |
| 정적분석 | BE: Spotless(Google Java Format) + Checkstyle / FE: Biome | 가벼운 게이트 |
| 보안 | gitleaks (pre-commit) + Trivy + Semgrep (CI) | H8/H10 |
| CI | **Jenkins** (`Jenkinsfile`, declarative), 자체도 docker-compose 운영 | 사용자 선택 — H9의 GitHub Actions 명세 **우회** |
| 인프라 (로컬) | docker-compose: mysql 8, redis 7, jenkins | 단순화 |
| 배포 | 로컬 docker-compose 한정, Jenkins는 lint→test→build까지 (배포 X) | 학습 범위 |

## 학습 친화 모드 (테스트 정책)

사용자는 테스트 미경험. 따라서:

- **H4 커버리지 임계 = 0%로 시작**, 도메인 안정 시점에 30% → 60% 단계 상향. JaCoCo 리포트는 항상 생성.
- **H5 통합 테스트 1종**은 "Testcontainers 부팅 + healthcheck 1개"만으로 충족(빈 깡통 OK).
- **AI 에이전트 규칙**: BE/FE 에이전트는 프로덕션 코드와 함께 **샘플/시드 테스트 1세트만** 생성. 신규 테스트 양산 금지. 사용자가 명시 요청 시에만 테스트 추가. → [`.harness/agent-prompts/implementer-be.md`](../../../.harness/agent-prompts/implementer-be.md), `implementer-fe.md`에 박제 예정.
- 두 번째 기능부터는 사용자가 테스트를 먼저 1개 작성 → AI에게 "이 테스트 만족하는 구현" 요청 (**선택적 TDD 체험**).
- Jenkins에서 test 실패는 당분간 `unstable`로 처리(빌드 차단 X). 안정화되면 `failure`로 격상.

## 멀티에이전트 분담 (초기)

- **Architect** = 메인 세션 (Opus 4.7 기본, Codex fallback 가능). 분해/머지 판정.
- **Implementer-A (BE)** = Sonnet 4.6 기본, Codex fallback 가능. `backend/` worktree.
- **Implementer-B (FE)** = Sonnet 4.6 기본, Codex fallback 가능. `frontend/` worktree.
- **Infra / CI** = Sonnet 4.6 기본, Codex fallback 가능. Docker/Jenkins/dev script worktree.
- **Implementer-C (Codex CLI)** = 공공 API ETL 스크립트(여행지 시드 적재) + 숙박 시드 생성/수집.
- **QA / Reviewer** = PR 시점 호출.

## 게이트 채택 / 우회

| 게이트 | 상태 | 비고 |
|---|---|---|
| H1 Conventional Commits + GitFlow-lite | 채택 | commit-msg 훅 |
| H2 lint/format | 채택 | Spotless+Checkstyle / Biome |
| H3 pre-commit | 채택 | gitleaks + lint |
| H4 커버리지 ≥ 60% | **단계적 채택** | 0% → 30% → 60% |
| H5 통합 테스트 1종 | 채택 | Testcontainers 깡통부터 |
| H6 산출물 4종 | 채택 | plan/diff/self-review/handoff |
| H7 PR 템플릿 | 채택 | `.github/pull_request_template.md` |
| H8 gitleaks + .gitignore | 채택 | |
| H9 GitHub Actions | **우회** | 대체: Jenkinsfile (사용자 결정) |
| H10 Trivy + Semgrep | 채택 | Jenkins 단계로 통합 |

## 부트스트랩 명령 (D 단계에서 실행 — 사용자 승인 후)

```bash
# 0) 인프라
mkdir -p infra/docker infra/jenkins
# infra/docker/docker-compose.yml: mysql:8, redis:7, jenkins/jenkins:lts-jdk21
# infra/jenkins/Jenkinsfile: declarative pipeline (lint→test→build→security)

# 1) 백엔드 (Spring Initializr CLI 또는 start.spring.io 다운로드)
mkdir -p backend && cd backend
# spring init: jdk 21, maven, web, data-jpa, data-redis, session-data-redis,
#              validation, security(추후), actuator, mysql, lombok, configuration-processor
# 추가 의존성: querydsl-jpa, querydsl-apt, testcontainers(mysql, junit-jupiter), spotless, checkstyle
cd ..

# 2) 프론트엔드
npm create vite@latest frontend -- --template vue-ts
cd frontend && npm i pinia vue-router && npm i -D vitest @vue/test-utils @playwright/test msw @biomejs/biome
cd ..

# 3) 시드 코드 + 시드 테스트 (AI가 1세트만 생성)
#    backend: HealthController + HealthControllerTest (단위), HealthIntegrationTest (Testcontainers)
#    frontend: App.vue 유지 + sample.spec.ts (Vitest), e2e/home.spec.ts (Playwright)

# 4) 게이트 활성화
pre-commit install --hook-type pre-commit --hook-type commit-msg
../.harness/scripts/harness-check.sh "$PWD"

# 5) develop 분기 + 첫 worktree
git checkout -b develop
cd .. && .harness/scripts/wt-new.sh 20-spec-tourdoum be feat-bootstrap
```

## 운영 메모 (2026-05-06 업데이트)

- 부트스트랩에서 도입한 dev 프로파일 분리(default=validate / dev=update)는 **학습 단계엔 과설계**로 판단되어 일시 폐기. 현재 `application.yml` 단일에 `ddl-auto=update`. ADR-0004(DB 마이그레이션 — Flyway/Liquibase) 도입 시점에 `validate`로 다시 전환하고 마이그레이션 도구가 스키마를 관리한다.
- **ADR-0004(2026-05-06 Accepted)으로 Flyway+validate 전환됨.** `ddl-auto=update`는 폐기, Flyway(`db/migration/V1__init.sql` baseline)가 스키마를 관리한다.
- 동일 이유로 `application-dev.yml`도 삭제. 환경별 차이가 다시 필요해지는 시점에 재도입.
- Redis 호스트 포트는 `tourdoum-redis` 컨테이너에서 **6380:6379**로 매핑(다른 프로젝트 redis와 충돌 회피). 앱 측 default도 `6380`으로 동기화.
- **ADR-0007(2026-05-07 Accepted)으로 ORM 결정 근거 박제됨.** JPA(Hibernate)+QueryDSL 채택의 명시적 비교표·근거 7개 항목 기록. 공간/집계 native+projection 패턴화 및 N+1 방지 가이드라인 후속 반영.

## 미해결 / 후속

- **데이터 출처 확정**: 한국관광공사 TourAPI 4.0(`KorService2`) 사용 가정. API 키 발급은 사용자 작업. `.env.example`에 `TOUR_API_KEY=` 추가 예정.
- **숙박 데이터 출처**: 1차는 가상 생성(여행지 좌표 반경 5km 내 N개 무작위 시드). 실제 수집(스크래핑) 여부는 ToS 검토 후 ADR-0002로 분리.
- **카카오 지도 키**: JavaScript 키 발급 후 `frontend/.env`에 보관. 도메인 등록 필요(localhost 등록).
- **소셜 로그인(카카오)**: ADR-0003에서 다룰 예정.
- **커버리지 상향 트리거**: 도메인 모듈(여행지/숙박/사용자) 모두 1차 CRUD 완료 시점에 30%로 상향. 이후 첫 통합 시나리오 통과 시 60%.
- **Jenkins 운영 모드**: 컨테이너 재기동 시 데이터 볼륨 정책 미확정. 일단 명명 볼륨(`jenkins_home`)으로.

## 실행 결과 로그 (D 단계 진행 시 추가)

(부트스트랩 명령 실행 후 각 단계 결과를 여기에 누적)
