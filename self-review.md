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

---

## build smoke (2026-05-06T12:01:49Z)

### backend: mvn verify (skipITs) — ✓ PASS

```
2026-05-06T21:02:12.382+09:00  INFO 9618 --- [           main] r$InitializeUserDetailsManagerConfigurer : Global AuthenticationManager configured with UserDetailsService bean with name inMemoryUserDetailsManager
2026-05-06T21:02:12.841+09:00  INFO 9618 --- [           main] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-05-06T21:02:12.841+09:00  INFO 9618 --- [           main] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-05-06T21:02:12.847+09:00  INFO 9618 --- [           main] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 6 ms
2026-05-06T21:02:12.926+09:00  INFO 9618 --- [           main] c.s.t.health.HealthControllerTest        : Started HealthControllerTest in 9.091 seconds (process running for 14.234)
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/Users/woolee/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
2026-05-06T21:02:17.985+09:00  INFO 9618 --- [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.ssafy.tourdoum.jpa.JpaSliceSampleTest]: JpaSliceSampleTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-05-06T21:02:18.201+09:00  INFO 9618 --- [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.ssafy.tourdoum.TourdoumApplication for test class com.ssafy.tourdoum.jpa.JpaSliceSampleTest
2026-05-06T21:02:18.227+09:00  INFO 9618 --- [           main] o.s.b.d.r.RestartApplicationListener     : Restart disabled due to context in which it is running

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.3.5)

2026-05-06T21:02:18.543+09:00  INFO 9618 --- [           main] c.ssafy.tourdoum.jpa.JpaSliceSampleTest  : Starting JpaSliceSampleTest using Java 21.0.6 with PID 9618 (started by woolee in /Users/woolee/SSAFY_Advance/SSAFY_Advance/20-spec-tourdoum/.worktrees/20-spec-tourdoum-be-feat-flyway/backend)
2026-05-06T21:02:18.545+09:00  INFO 9618 --- [           main] c.ssafy.tourdoum.jpa.JpaSliceSampleTest  : No active profile set, falling back to 1 default profile: "default"
2026-05-06T21:02:19.432+09:00  INFO 9618 --- [           main] .s.d.r.c.RepositoryConfigurationDelegate : Multiple Spring Data modules found, entering strict repository configuration mode
2026-05-06T21:02:19.441+09:00  INFO 9618 --- [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-05-06T21:02:19.676+09:00  INFO 9618 --- [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 207 ms. Found 1 JPA repository interface.
2026-05-06T21:02:19.930+09:00  INFO 9618 --- [           main] beddedDataSourceBeanFactoryPostProcessor : Replacing 'dataSource' DataSource bean with embedded version
2026-05-06T21:02:20.975+09:00  INFO 9618 --- [           main] o.s.j.d.e.EmbeddedDatabaseFactory        : Starting embedded database: url='jdbc:h2:mem:6124a8c9-4ca6-4b4c-919b-d02555e26658;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false', username='sa'
2026-05-06T21:02:23.290+09:00  INFO 9618 --- [           main] o.hibernate.jpa.internal.util.LogHelper  : HHH000204: Processing PersistenceUnitInfo [name: default]
2026-05-06T21:02:23.509+09:00  INFO 9618 --- [           main] org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.5.3.Final
2026-05-06T21:02:23.610+09:00  INFO 9618 --- [           main] o.h.c.internal.RegionFactoryInitiator    : HHH000026: Second-level cache disabled
2026-05-06T21:02:24.092+09:00  INFO 9618 --- [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-05-06T21:02:24.294+09:00  WARN 9618 --- [           main] org.hibernate.orm.deprecation            : HHH90000025: H2Dialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-05-06T21:02:26.413+09:00  INFO 9618 --- [           main] o.h.e.t.j.p.i.JtaPlatformInitiator       : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
Hibernate: drop table if exists members cascade
Hibernate: create table members (created_at timestamp(6) not null, id bigint generated by default as identity, updated_at timestamp(6) not null, nickname varchar(50) not null unique, email varchar(255) not null unique, password varchar(255) not null, role enum ('ROLE_ADMIN','ROLE_USER') not null, primary key (id))
2026-05-06T21:02:26.528+09:00  INFO 9618 --- [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-05-06T21:02:27.766+09:00  INFO 9618 --- [           main] c.ssafy.tourdoum.jpa.JpaSliceSampleTest  : Started JpaSliceSampleTest in 9.54 seconds (process running for 29.074)
```

### frontend: npm run build — ✗ FAIL

```
sh: vue-tsc: command not found
```

### frontend: npm test — ✗ FAIL

```
sh: vitest: command not found
```
