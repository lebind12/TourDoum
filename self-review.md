# self-review — be/feat-flyway

| Gate | 결과 | 비고 |
|---|---|---|
| H1 commit conv | PASS | feat(be)/docs(be)/chore(be) Conventional Commits 준수 |
| H2 lint/format | PASS | Spotless 26파일 clean, Checkstyle WARNING only (failOnViolation=false) |
| H3 pre-commit | PASS | 모든 커밋에서 gitleaks/trailing-whitespace/yaml-check 통과 |
| H4 unit + cov | PASS | Tests run: 4, Failures: 0 / JaCoCo 리포트 생성 (임계 0%) |
| H5 integration | SKIP | AuthIntegrationTest — @EnabledIfSystemProperty "tourdoum.it"=true (Docker 조건 skip 정상) |
| H6 artifacts | PASS | plan.md / self-review.md / handoff.md 갱신 |
| H7 PR template | N/A | PR 시점에 적용 |
| H8 secrets/.gitignore | PASS | 환경변수 처리 유지, gitleaks 통과 |
| H9 CI | 우회 | ADR-0001: Jenkins 대체 (사용자 결정) |
| H10 sec-scan | N/A | Jenkins 파이프라인 단계 (향후) |

## 빌드 결과

```
./mvnw -DskipITs verify (JAVA_HOME=liberica-jdk-21)
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (52.694 s)
```

통과 테스트:
- `HealthControllerTest`
- `MemberServiceTest`
- `JpaSliceSampleTest` (H2 + ddl-auto=create-drop + flyway.enabled=false)
- `AuthIntegrationTest` — `tourdoum.it` 시스템 프로퍼티 미충족으로 skip (정상)

## 컬럼 정합 확인 (Member.java vs V1__init.sql)

| 필드 | JPA 매핑 | SQL | 일치 |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT PK | BIGINT AUTO_INCREMENT PK | O |
| `email` | VARCHAR(255) NOT NULL UNIQUE | VARCHAR(255) NOT NULL UNIQUE KEY | O |
| `password` | VARCHAR(255) NOT NULL | VARCHAR(255) NOT NULL | O |
| `nickname` | VARCHAR(50) NOT NULL UNIQUE | VARCHAR(50) NOT NULL UNIQUE KEY | O |
| `role` | VARCHAR(20) NOT NULL (@Enumerated(STRING)) | VARCHAR(20) NOT NULL | O |
| `created_at` | TIMESTAMP(6) NOT NULL (updatable=false) | TIMESTAMP(6) NOT NULL | O |
| `updated_at` | TIMESTAMP(6) NOT NULL | TIMESTAMP(6) NOT NULL | O |

## 보안 점검

- `V1__init.sql`: 하드코딩 시크릿 없음.
- `application.yml` (main): 모든 민감값 `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}` 환경변수 처리 유지.
- gitleaks pre-commit: 모든 커밋 통과.

## 주의 사항

- H2 슬라이스 테스트 로그에서 `role`이 `enum('ROLE_ADMIN','ROLE_USER')`로 DDL 생성됨.
  이는 H2 `create-drop` 자체 DDL 생성 동작이며, Flyway 비활성 상태에서 V1 SQL과 무관.
- MySQL `ddl-auto=validate`+Flyway 조합은 Docker 기동 환경에서만 실제 검증 가능.
