# ADR 0004 — DB 마이그레이션 도구 도입

- 작성일: 2026-05-06
- 상태: **Accepted**
- 작성자: Implementer-A (BE sub-agent, Claude Sonnet 4.6) / 승인: Architect

---

## 컨텍스트

ADR-0001 §운영 메모에서 `ddl-auto=update` 단일 설정을 "학습 단계 임시"로 정의하고,
마이그레이션 도구 도입 시점에 `validate`로 전환하기로 예고했다.
회원 도메인(ADR-0003)이 확정됨에 따라 스키마 버전 관리가 필요해졌다.

---

## 결정: Flyway 채택

**도구: Flyway (Community Edition)**

대안으로 Liquibase(XML/YAML/JSON 형식, rollback 지원)를 검토했으나,
SQL 직관성과 Spring Boot BOM 내장 지원을 이유로 Flyway를 채택한다.
단, Flyway Community에는 `undo` 명령이 없으므로 롤백은 **보상 마이그레이션**으로 처리한다.

---

## 마이그레이션 파일 위치

```
backend/src/main/resources/db/migration/
├── V1__init.sql         ← develop 시점 baseline (members 테이블)
└── V2__attraction.sql   ← attraction worktree 예약 (본 worktree 미작성)
```

명명 규칙: `V{n}__{description}.sql` (대문자 V, 숫자, 이중 언더스코어, snake_case 설명)

---

## 스키마 관리 정책

| 환경 | `ddl-auto` | Flyway 활성 |
|---|---|---|
| 운영/개발 (MySQL) | `validate` | 활성 (`enabled=true`) |
| 단위/슬라이스 테스트 (`@DataJpaTest`, `@SpringBootTest` 슬라이스) | `create-drop` | **비활성** (`enabled=false`) |
| 통합 테스트 (Testcontainers MySQL) | `update` (DynamicPropertySource override) | 비활성 (현행 유지) |

> 슬라이스 테스트에서 Flyway를 끄는 이유:
> H2 인메모리 DB와 MySQL 방언 DDL(`ENGINE=InnoDB`, `COLLATE` 등)이 충돌하지 않도록.

---

## baseline 정책

- **V1**: develop 분기 기준 모든 테이블 생성 스크립트. 현재는 `members` 테이블만.
- `baseline-on-migrate: true` — 기존 스키마가 있는 DB에서 최초 Flyway 적용 시 V1을 baseline으로 처리.
- 기존 마이그레이션 파일 수정 금지. 체크섬이 깨지면 Flyway가 부팅을 거부한다.
  변경 필요 시 새 `V{n+1}__compensate.sql`을 작성한다.

---

## rollback 정책

Flyway Community는 `undo` 미지원.

- **개발 환경**: `./mvnw flyway:repair` 후 체크섬 재계산.
  깨진 마이그레이션은 삭제 후 새 번호로 재작성.
- **운영 환경**: 보상 마이그레이션(`V{n+1}__rollback_xxx.sql`)으로 역변환.
  데이터 유실 가능성 있는 작업(`DROP COLUMN` 등)은 사전에 백업 필수.

---

## 미해결

- **Flyway Testcontainers 통합**: 통합 테스트(Testcontainers MySQL)에서 Flyway를 활성화하면
  스키마가 마이그레이션 기준으로 관리되는 이점이 있다. 현재는 `ddl-auto=update` override 유지.
  안정화 후 활성화 검토 (후속 ADR 또는 handoff 항목).
- **V2 예약**: attraction worktree가 `V2__attraction.sql` 번호 사용 예정.
  충돌 방지를 위해 본 worktree는 V1까지만 생성한다.
