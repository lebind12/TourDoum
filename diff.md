# diff — be/feat-flyway

_generated: 2026-05-06T12:01:49Z_

## stat (vs HEAD~ … 또는 base)

```
 .env.example                                       |   22 +-
 .github/workflows/ci.yml                           |   76 -
 .gitignore                                         |   12 +-
 .harness-hooks/check-commit-msg.sh                 |   24 +
 .java-version                                      |    1 +
 .pre-commit-config.yaml                            |    4 +-
 backend/.env.example                               |   17 +
 backend/.gitignore                                 |   30 +
 backend/.java-version                              |    1 +
 backend/.mvn/wrapper/maven-wrapper.properties      |    3 +
 backend/README.md                                  |  124 +
 backend/checkstyle-suppressions.xml                |   11 +
 backend/mvnw                                       |  295 ++
 backend/mvnw.cmd                                   |  189 +
 backend/pom.xml                                    |  349 ++
 .../com/ssafy/tourdoum/TourdoumApplication.java    |   12 +
 .../com/ssafy/tourdoum/auth/AuthController.java    |   33 +
 .../tourdoum/auth/JsonAuthenticationFilter.java    |   49 +
 .../java/com/ssafy/tourdoum/auth/LoginRequest.java |    4 +
 .../java/com/ssafy/tourdoum/auth/MeResponse.java   |   12 +
 .../ssafy/tourdoum/auth/MemberDetailsService.java  |   34 +
 .../com/ssafy/tourdoum/auth/SecurityConfig.java    |  166 +
 .../com/ssafy/tourdoum/global/ErrorResponse.java   |    4 +
 .../tourdoum/global/GlobalExceptionHandler.java    |   49 +
 .../ssafy/tourdoum/global/JpaAuditingConfig.java   |    9 +
 .../ssafy/tourdoum/global/RedisSessionConfig.java  |   36 +
 .../ssafy/tourdoum/health/HealthController.java    |   21 +
 .../tourdoum/member/DuplicateEmailException.java   |    9 +
 .../member/DuplicateNicknameException.java         |    9 +
 .../java/com/ssafy/tourdoum/member/Member.java     |   61 +
 .../ssafy/tourdoum/member/MemberController.java    |   29 +
 .../ssafy/tourdoum/member/MemberRepository.java    |   13 +
 .../java/com/ssafy/tourdoum/member/MemberRole.java |    7 +
 .../com/ssafy/tourdoum/member/MemberService.java   |   47 +
 .../com/ssafy/tourdoum/member/SignupRequest.java   |   11 +
 .../com/ssafy/tourdoum/member/SignupResponse.java  |    9 +
 backend/src/main/resources/application.yml         |   52 +
 .../src/main/resources/db/migration/V1__init.sql   |   32 +
 .../tourdoum/health/HealthControllerTest.java      |   38 +
 .../tourdoum/integration/AuthIntegrationTest.java  |  120 +
 .../integration/HealthIntegrationTest.java         |   75 +
 .../com/ssafy/tourdoum/jpa/JpaSliceSampleTest.java |   42 +
 .../ssafy/tourdoum/member/MemberServiceTest.java   |   64 +
 backend/src/test/resources/application.yml         |   27 +
 docs/adr/0001-bootstrap.md                         |    7 +
 docs/adr/0002-spatial-index.md                     |   13 +
 docs/adr/0003-auth-session.md                      |   75 +
 docs/adr/0004-db-migration.md                      |   78 +
 frontend/.env.example                              |    2 +
 frontend/.gitignore                                |   23 +
 frontend/README.md                                 |   87 +
 frontend/biome.json                                |   41 +
 frontend/e2e/home.spec.ts                          |    9 +
 frontend/index.html                                |   22 +
 frontend/package-lock.json                         | 4195 ++++++++++++++++++++
 frontend/package.json                              |   33 +
 frontend/playwright.config.ts                      |   38 +
 frontend/src/App.vue                               |   29 +
 frontend/src/api/client.ts                         |   27 +
 frontend/src/main.ts                               |   12 +
 frontend/src/router/index.ts                       |   15 +
 frontend/src/stores/health.ts                      |   31 +
 frontend/src/views/HomeView.vue                    |   63 +
 frontend/src/views/__tests__/HomeView.spec.ts      |   58 +
 frontend/tsconfig.app.json                         |   27 +
 frontend/tsconfig.json                             |    7 +
 frontend/tsconfig.node.json                        |   20 +
 frontend/vite.config.ts                            |   21 +
 handoff.md                                         |   35 +
 infra/README.md                                    |   32 +
 infra/docker/docker-compose.yml                    |   67 +
 infra/jenkins/Jenkinsfile                          |  122 +
 infra/jenkins/README.md                            |  140 +
 lint/biome.json                                    |   42 +-
 plan.md                                            |   58 +
 scripts/README.md                                  |   86 +
 scripts/dev-down.sh                                |   11 +
 scripts/dev-up.sh                                  |   21 +
 scripts/dev-watch-be.sh                            |   39 +
 self-review.md                                     |   52 +
 80 files changed, 7768 insertions(+), 102 deletions(-)
```

## untracked

```
```
