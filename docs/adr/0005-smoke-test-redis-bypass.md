# ADR-0005: @SpringBootTest 스모크 테스트에서 Spring Session Redis 우회 패턴

- **날짜**: 2026-05-07
- **상태**: Accepted
- **결정자**: Backend Implementer (be/feat-context-smoke-test)

---

## 맥락

Spring Boot 3.3.5 + `spring-session-data-redis` 스택에서 `@SpringBootTest contextLoads()` 를
Docker 없이 실행하면 실패한다.

### 원인 분석

```
RedisSessionConfig
  @Configuration
  @EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800)
```

`@EnableRedisIndexedHttpSession` 은 사용자 정의 `@Configuration` 에 붙은 어노테이션으로,
Spring Boot의 `SessionAutoConfiguration`(자동설정) 과 별개로 동작한다.

`test/application.yml` 에 `spring.session.store-type=none` 을 설정해도:

1. Spring Boot `SessionAutoConfiguration` → `NoOpSessionConfiguration` 선택 (의도대로 동작)
2. **그러나** `RedisSessionConfig` 는 자동설정이 아닌 사용자 `@Configuration` 이므로
   프로퍼티를 무시하고 `RedisIndexedHttpSessionConfiguration` 을 import
3. `RedisIndexedSessionRepository` 빈 초기화 시 `afterPropertiesSet()` 호출
4. → `enableKeyspaceNotifications(connectionFactory)` → `connectionFactory.getConnection()`
5. → localhost:6379 또는 설정된 Redis에 TCP 연결 시도 → **연결 실패 → 컨텍스트 로드 실패**

---

## 결정: `@ConditionalOnProperty` 추가

`RedisSessionConfig` 클래스에 아래 어노테이션을 추가한다:

```java
@ConditionalOnProperty(
    name = "spring.session.store-type",
    havingValue = "redis",
    matchIfMissing = true)
```

### 이유

`spring.session.store-type` 이 `redis` 일 때만 Redis 세션 설정을 로드하는 것이
의미론적으로 정확하다. `@EnableRedisIndexedHttpSession` 이 "Redis 세션을 쓰겠다"는 선언이므로,
해당 프로퍼티가 `redis` 인 경우에만 활성화되어야 한다.

`matchIfMissing = true` 로 설정하는 이유: 프로퍼티가 누락될 경우 기존 동작(Redis 세션 활성화)을
유지하여 묵시적 비활성화로 인한 운영 장애를 방지한다.

---

## 기각된 대안

### 옵션 1: `@TestConfiguration` mock `RedisConnectionFactory`

프로덕션 코드를 건드리지 않는 장점이 있으나:
- `RedisIndexedSessionRepository.afterPropertiesSet()` 내부에서 `serverCommands().getConfig()` 등을
  호출하므로, Mockito mock 객체가 null을 반환할 경우 NPE 가능성
- `spring.session.redis.configure-action=none` 을 추가해야 완전히 안전
- 유지보수 시 Spring Session 내부 구현 변경에 취약

### 옵션 2: `spring.autoconfigure.exclude` (test application.yml)

`spring.autoconfigure.exclude` 는 Spring Boot **자동설정** 클래스만 제외한다.
`RedisSessionConfig` 는 사용자 정의 `@Configuration` 이므로 이 메커니즘이 통하지 않는다. **근본 해결 불가**.

### 옵션 3: `@Profile("!test")` on `RedisSessionConfig`

동작은 하지만:
- 모든 `@SpringBootTest` 에 `@ActiveProfiles("test")` 를 강제 요구
- 기존 `AuthIntegrationTest` 는 `@ActiveProfiles` 가 없으므로 Redis 세션 설정이 의도대로 로드됨 (문제 없음)
- 그러나 미래 테스트 작성자가 프로파일 규약을 모르면 Redis 세션 로드 여부가 불일치 발생 가능
- "테스트 환경이다"는 것을 표현하기 위해 프로파일을 쓰는 것보다, "Redis 세션을 쓰는 환경이다"를 프로퍼티로
  표현하는 것이 더 의미론적으로 명확하다.

---

## 결과

- `RedisSessionConfig` 에 `@ConditionalOnProperty` 1줄 추가
- `ApplicationContextSmokeTest.java` 신규 추가 (`@SpringBootTest contextLoads()`)
- 기존 `AuthIntegrationTest` 는 변경 없음 (Testcontainers Redis 사용, `@DynamicPropertySource` 로
  `store-type` 오버라이드 없이도 동작 — main `application.yml` 의 `store-type: redis` 가 그대로 적용됨)
- `./mvnw verify` (Docker 없이) 에서 `contextLoads` 통과

---

## 주의 사항

- 프로덕션 `application.yml` 에서 `spring.session.store-type: redis` 가 제거되면 `RedisSessionConfig`
  가 로드되지 않아 Redis 세션이 비활성화된다 (matchIfMissing=true 로 완화됨).
- `RedisAutoConfiguration`(LettuceConnectionFactory) 은 여전히 로드되지만, Lettuce 는 실제 Redis 명령
  실행 전까지 TCP 연결을 맺지 않으므로 스모크 테스트에서 문제가 발생하지 않는다.
