# handoff — be/feat-flyway

## 완료 내용 요약

- ADR-0004 Accepted 박제 (docs/adr/0004-db-migration.md)
- Flyway 의존성 추가 (flyway-core + flyway-mysql, Spring Boot 3.3 BOM)
- `application.yml` (main): `ddl-auto=validate` + `spring.flyway.enabled=true`
- `application.yml` (test): `spring.flyway.enabled=false` + `ddl-auto=create-drop` (기존 유지)
- `db/migration/V1__init.sql`: members 테이블 MySQL 8 baseline DDL
- ADR-0001 운영 메모 갱신 ("ADR-0004(2026-05-06 Accepted)으로 Flyway+validate 전환됨")
- `backend/README.md` DB 마이그레이션 섹션 추가
- `./mvnw -DskipITs verify` BUILD SUCCESS, Tests run: 4, Failures: 0

## 결정이 필요한 항목

1. **AuthIntegrationTest Flyway 활성화 여부**
   현재 `DynamicPropertySource`에서 `ddl-auto=update`로 override해 Flyway를 우회.
   통합 테스트에서도 Flyway를 활성화하려면 `ddl-auto=validate`로 변경 + `flyway.enabled=true` 추가 필요.
   결정: Architect 또는 QA worktree.

2. **V2 마이그레이션 번호 예약 충돌**
   attraction worktree가 `V2__attraction.sql` 번호 사용 예정.
   attraction worktree와 본 브랜치 머지 순서를 Architect가 명시해야 한다.
   (attraction이 먼저 develop에 머지되면 V2가 먼저 들어옴 — 본 worktree는 V1까지만 생성했으므로 충돌 없음)

## 미해결 질문

- `baseline-on-migrate: true` 설정은 이미 스키마가 있는 DB(초기 `ddl-auto=update`로 생성된 환경)에서 V1을 건너뛰고 기동할 수 있게 한다. 새 DB(빈 DB)에서는 V1이 그대로 실행된다. 기존 개발 DB가 있다면 개발자가 `flyway_schema_history` 테이블을 수동 확인해야 할 수 있다.
- Flyway Community rollback 없음 — 운영 환경 보상 마이그레이션 절차를 팀이 합의해야 함 (ADR-0004 §rollback 정책 참고).

## 다음 단계 제안

1. **attraction worktree** (`be/feat-attraction` 또는 유사): `V2__attraction.sql` 작성 + 관련 엔티티 구현.
2. **머지 순서**: 본 브랜치(`be/feat-flyway`) → develop 머지 선행 권장. 이후 attraction 브랜치 머지 시 V2가 자연스럽게 이어진다.
3. **통합 테스트 Flyway 활성화**: 안정화 후 별도 worktree 또는 QA worktree에서 처리.
