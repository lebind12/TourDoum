# handoff — be/feat-context-smoke-test

**브랜치:** `be/feat-context-smoke-test` → `develop` 머지 대상
**작업자:** Backend Implementer | **날짜:** 2026-05-07

## 완료 항목

| 항목 | 상태 |
|---|---|
| ADR-0005 작성 (옵션 분석 + @ConditionalOnProperty 채택 근거) | ✅ |
| `RedisSessionConfig`: `@ConditionalOnProperty(store-type=redis, matchIfMissing=true)` 추가 | ✅ |
| `ApplicationContextSmokeTest`: `@SpringBootTest contextLoads()` 신규 추가 | ✅ |
| `mvnw verify -DskipITs` 12/12 통과 (Docker 불필요) | ✅ |
| agent-finalize build smoke all green | ✅ |
| Conventional Commits + pre-commit 훅 통과 | ✅ |
| Spotless (Google Java Format) 통과 | ✅ |

## 결정 포인트 (Reviewer/Architect 확인 요청)

### 1. `@ConditionalOnProperty` matchIfMissing=true 동작
프로덕션 `application.yml`에 `spring.session.store-type: redis` 가 명시되어 있으므로 즉시 영향 없음.
단, 향후 해당 프로퍼티가 제거되면 `RedisSessionConfig`가 로드됨 (Redis 세션 활성 = 기대 동작).

### 2. AuthIntegrationTest 영향
`AuthIntegrationTest`는 `@DynamicPropertySource`로 `spring.data.redis.*` 를 오버라이드하나
`spring.session.store-type`을 별도 오버라이드하지 않음 → main `application.yml`의 `store-type: redis` 가 그대로 적용되어 `RedisSessionConfig` 로드 ✅.

### 3. 프론트엔드 `npm install` 선행 필요 (기존 이슈)
agent-finalize.sh의 BASE 계산이 `origin/HEAD` 심볼릭 ref 누락으로 `main` 으로 폴백 → diff에 `frontend/` 전체 포함 → FE 빌드도 smoke 대상에 포함됨. `node_modules` 미설치 상태에서 false-fail 발생.
→ 이번 worktree에서 `npm install` 로 임시 해소. 근본 해결은 `origin HEAD` 설정 또는 finalize.sh BASE 계산 로직 개선 필요 (Architect/infra 결정 대기).

## 미해결 질문

없음. ADR 결정 완료.

## AuthIntegrationTest 동일 문제 (#7 보고 명시)

Task 할당 메시지에서 "AuthIntegrationTest의 동일 미해결도 같이 처리 가능"이라 명시됨.
`AuthIntegrationTest`는 Testcontainers(MySQL+Redis)를 사용하는 IT 테스트로, `store-type` 오버라이드가 없어도
`@DynamicPropertySource`가 Redis 연결 정보를 제공하므로 `RedisSessionConfig`가 정상 동작함.
→ **별도 수정 불필요**. 단, `tourdoum.it=true` 없이 실행 시 `@EnabledIfSystemProperty` 로 자동 skip됨.

## 다음 단계 제안

1. Reviewer: `be/feat-context-smoke-test` → `develop` PR 생성 및 리뷰
2. CI에서 `contextLoads` 스모크 테스트가 항상 실행되는지 확인
3. Architect: `origin HEAD` 심볼릭 ref 설정 또는 finalize.sh BASE 로직 개선 결정
