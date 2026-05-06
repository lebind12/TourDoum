# plan — be/feat-context-smoke-test

**역할:** Backend Implementer | **브랜치:** `be/feat-context-smoke-test`

## 목표

`@SpringBootTest contextLoads()` 스모크 테스트를 `./mvnw verify` (Docker 없이)에 추가 통과시킨다.
Spring Boot 3.3.5 + spring-session-data-redis 조합에서 `store-type=none`이어도
`@EnableRedisIndexedHttpSession` 이 Redis 연결을 시도하는 문제를 우회하는 패턴을 결정하고 박제한다.

## 스펙 출처

- Task #1 (team-lead 할당)
- handoff 메시지: "Spring Session Redis 우회 / contextLoads 스모크 패턴"
- `implementer-be.md` §종료 조건: "@SpringBootTest contextLoads() 1개가 존재해야 한다"

## 문제 분석

```
RedisSessionConfig
  @Configuration
  @EnableRedisIndexedHttpSession   ← 사용자 @Configuration. AutoConfiguration이 아님.
```

`spring.session.store-type=none` (test application.yml)은 Spring Boot `SessionAutoConfiguration`
(자동설정)만 우회한다. `@EnableRedisIndexedHttpSession` 이 붙은 사용자 `@Configuration`은
프로퍼티를 무시하고 `RedisIndexedHttpSessionConfiguration` 을 import → `RedisIndexedSessionRepository`
빈 초기화 → `afterPropertiesSet()` 에서 `connectionFactory.getConnection()` → Redis 연결 실패.

## 옵션 비교

| 옵션 | 변경 위치 | 장점 | 단점 |
|---|---|---|---|
| 1. `@TestConfiguration` mock `RedisConnectionFactory` | test 전용 | 프로덕션 코드 무변경 | mock stubbing 범위 추적 필요; `enableKeyspaceNotifications` 호출 시 NPE 위험 |
| 2. `spring.autoconfigure.exclude` (test yml) | test yml | 선언적 | `RedisSessionConfig`는 AutoConfiguration이 아니라 exclude가 통하지 않음 — 근본 해결 불가 |
| 3. `@Profile("!test")` on `RedisSessionConfig` | 프로덕션 | 패턴 명확 | 모든 테스트에 `@ActiveProfiles("test")` 강제 → 기존 IT 테스트와 충돌 위험 |
| **4. `@ConditionalOnProperty(store-type=redis)` on `RedisSessionConfig`** | **프로덕션 (1줄)** | **의미론 명확, matchIfMissing=true로 기본값 보호** | 프로덕션 파일 미세 변경 |

## ADR 결정: 옵션 4 (@ConditionalOnProperty) 채택

`@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis", matchIfMissing = true)`
를 `RedisSessionConfig`에 추가.

이유:
- 옵션 2는 근본 해결 불가 (배제)
- 옵션 3은 기존 `AuthIntegrationTest`(@ActiveProfiles 없음)에 영향 없지만, 미래 테스트가
  프로파일 규약을 따르지 않으면 Redis 세션 로드 여부가 불일치할 수 있다.
- 옵션 1은 mock stubbing이 복잡하며 `RedisIndexedSessionRepository.afterPropertiesSet()` 내부 동작
  변경에 취약.
- 옵션 4는 "이 빈은 Redis 세션이 설정된 환경에서만 유효하다"를 코드 자체가 선언.
  matchIfMissing=true → 프로퍼티 누락 시 기존 동작(Redis 세션) 유지.

## 변경 파일 후보

| 파일 | 종류 | 내용 |
|---|---|---|
| `backend/src/main/java/…/global/RedisSessionConfig.java` | 프로덕션 수정 | `@ConditionalOnProperty` 1줄 추가 |
| `backend/src/test/java/…/ApplicationContextSmokeTest.java` | 신규 | `@SpringBootTest contextLoads()` |
| `docs/adr/0005-smoke-test-redis-bypass.md` | 신규 ADR | 결정 박제 |

## 테스트 전략

- `./mvnw test -pl backend` (Surefire — Docker 없이) → contextLoads 통과
- 기존 슬라이스/단위 테스트 회귀 없음 확인
- `AuthIntegrationTest` (`@EnabledIfSystemProperty(tourdoum.it=true)`) 는 Surefire에서 제외되므로 영향 없음

## 리스크

- `@ConditionalOnProperty`가 있는 상태에서 프로덕션 `application.yml`의 `store-type: redis` 가 누락되면
  Redis 세션 비활성화. → matchIfMissing=true로 완화.
- `RedisAutoConfiguration`(LettuceConnectionFactory)은 여전히 로드되나, Lettuce는 실제 명령 실행 전까지
  TCP 연결을 맺지 않으므로 스모크 테스트에서 문제 없음.
