# diff — be/feat-bootstrap

_generated: 2026-05-06T09:36:26Z_

## stat (vs HEAD~ … 또는 base)

```
 .env.example                                       |  22 +-
 .gitignore                                         |   5 +-
 backend/.env.example                               |  17 ++
 backend/.gitignore                                 |  30 ++
 backend/.mvn/wrapper/maven-wrapper.properties      |   3 +
 backend/README.md                                  |  87 ++++++
 backend/checkstyle-suppressions.xml                |  11 +
 backend/mvnw                                       | 295 ++++++++++++++++++++
 backend/mvnw.cmd                                   | 189 +++++++++++++
 backend/pom.xml                                    | 310 +++++++++++++++++++++
 .../com/ssafy/tourdoum/TourdoumApplication.java    |  12 +
 .../ssafy/tourdoum/health/HealthController.java    |  21 ++
 backend/src/main/resources/application-dev.yml     |  13 +
 backend/src/main/resources/application.yml         |  40 +++
 .../tourdoum/health/HealthControllerTest.java      |  33 +++
 .../integration/HealthIntegrationTest.java         |  75 +++++
 .../com/ssafy/tourdoum/jpa/JpaSliceSampleTest.java |  42 +++
 backend/src/test/resources/application.yml         |  17 ++
 infra/README.md                                    |  32 +++
 infra/docker/docker-compose.yml                    |  57 ++++
 infra/jenkins/Jenkinsfile                          | 122 ++++++++
 plan.md                                            |  46 +++
 22 files changed, 1474 insertions(+), 5 deletions(-)
```

## untracked

```
diff.md
handoff.md
self-review.md
```
