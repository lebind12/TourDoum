# plan — be/feat-flyway

## 목표

`ddl-auto=update` 단일 설정을 Flyway 마이그레이션 + `ddl-auto=validate`로 전환한다.
- ADR-0004(DB 마이그레이션 도구 결정)를 placeholder → Accepted로 박제
- pom.xml에 flyway-core + flyway-mysql 의존성 추가 (Spring Boot 3.3 BOM 관리)
- `application.yml` (main): `ddl-auto=validate` + `spring.flyway` 활성화
- `src/test/resources/application.yml`: `spring.flyway.enabled=false` + `ddl-auto=create-drop`
- `db/migration/V1__init.sql`: Member 엔티티와 완전 정합하는 MySQL 8 DDL
- ADR-0001 운영 메모에 "ADR-0004(2026-05-06 Accepted)으로 Flyway+validate 전환됨" 추가
- `backend/README.md`에 DB 마이그레이션 운용 섹션 추가

## 스펙 출처

- `docs/adr/0001-bootstrap.md` §운영 메모 — ddl-auto=update 단일 설정 현황
- `docs/adr/0004-db-migration.md` — placeholder 상태 (본 worktree에서 Accepted로)
- `backend/src/main/java/com/ssafy/tourdoum/member/Member.java` — 컬럼 정합 기준
- CLAUDE.md §2 worktree 규칙, §5 하네스 게이트 H4/H5
- 작업 지시 (사용자, 2026-05-06)

## 변경 후보 파일

- `docs/adr/0004-db-migration.md` (신규)
- `docs/adr/0001-bootstrap.md` (운영 메모 1줄 추가)
- `backend/pom.xml` (flyway-core + flyway-mysql 의존성)
- `backend/src/main/resources/application.yml` (ddl-auto + flyway 섹션)
- `backend/src/main/resources/db/migration/V1__init.sql` (신규)
- `backend/src/test/resources/application.yml` (flyway.enabled=false)
- `backend/README.md` (마이그레이션 섹션 추가)
- `handoff.md`, `self-review.md`, `diff.md`

## 컬럼 정합 메모 (Member.java 분석)

| JPA 매핑 | SQL 컬럼 | 타입 | nullable | unique |
|---|---|---|---|---|
| `@Id` BIGINT IDENTITY | `id` | BIGINT AUTO_INCREMENT | NOT NULL | PK |
| `email` length=255 unique | `email` | VARCHAR(255) | NOT NULL | UK |
| `password` length=255 | `password` | VARCHAR(255) | NOT NULL | - |
| `nickname` length=50 unique | `nickname` | VARCHAR(50) | NOT NULL | UK |
| `@Enumerated(STRING)` length=20 | `role` | VARCHAR(20) | NOT NULL | - |
| `@CreatedDate` updatable=false | `created_at` | TIMESTAMP(6) | NOT NULL | - |
| `@LastModifiedDate` | `updated_at` | TIMESTAMP(6) | NOT NULL | - |

Hibernate 컬럼명 전략: `createdAt` → `created_at`, `updatedAt` → `updated_at` (Spring Boot 기본 implicit naming strategy)

## 테스트 전략

- 단위/슬라이스: `@DataJpaTest` — H2 + `ddl-auto=create-drop` + `flyway.enabled=false` (기존 JpaSliceSampleTest 통과 필수)
- 통합: `AuthIntegrationTest` — Testcontainers MySQL + `ddl-auto=update` (override 중). Flyway 비활성 상태. 현행 유지.
- 빌드 검증: `./mvnw -DskipITs verify` 통과 확인

## 리스크 / 미해결

1. **AuthIntegrationTest DynamicPropertySource**: `ddl-auto=update`를 override하므로 Flyway가 적용되지 않음. 통합 테스트에서 Flyway 없이 스키마가 만들어짐 — V1 SQL과 스키마 불일치 여지 없음(무관).
2. **V2 충돌 회피**: attraction worktree가 V2 번호 사용 예정 → 본 worktree는 V1까지만 생성. V2 예약 공지를 handoff에 기록.
3. **Flyway Community rollback 없음**: 보상 마이그레이션(V{n+1}) 방식을 운용 가이드에 명시.
4. **H2 호환**: V1 SQL에 MySQL 전용 `ENGINE=InnoDB` 등이 있어도 테스트 환경은 H2 `create-drop`으로 우회 — 문제 없음.
