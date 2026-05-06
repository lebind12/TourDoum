# plan — be/feat-bootstrap

## 목표

TourDoum 백엔드 프로젝트의 뼈대(scaffold)를 구성한다. 여행지/숙박 도메인 코드는 이 브랜치에서 작성하지 않는다.
- Spring Boot 3.3.x + JDK 21 + Maven 프로젝트 구조 생성
- `GET /api/health` 엔드포인트 구현 (학습용 커스텀 헬스체크)
- 단위·슬라이스·통합(Testcontainers) 시드 테스트 1세트
- Maven Wrapper 포함, `.env`/시크릿 없이 부팅 가능

## 스펙 출처

- `/Users/woolee/SSAFY_Advance/SSAFY_Advance/20-spec-tourdoum/docs/adr/0001-bootstrap.md`
- Architect 작업 지시 (Implementer-A 호출 프롬프트)
- CLAUDE.md §2, §6

## 변경 후보 파일

- `backend/pom.xml`
- `backend/src/main/java/com/ssafy/tourdoum/TourdoumApplication.java`
- `backend/src/main/java/com/ssafy/tourdoum/health/HealthController.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/test/java/com/ssafy/tourdoum/health/HealthControllerTest.java`
- `backend/src/test/java/com/ssafy/tourdoum/jpa/JpaSliceSampleTest.java`
- `backend/src/test/java/com/ssafy/tourdoum/integration/HealthIntegrationTest.java`
- `backend/.mvn/wrapper/maven-wrapper.properties`
- `backend/mvnw` (shell script)
- `backend/mvnw.cmd` (Windows script)
- `backend/README.md`
- `backend/.env.example`

## 테스트 전략

- 단위: `@WebMvcTest(HealthController.class)` + MockMvc — `/api/health` 200 + JSON body 검증
- 슬라이스(JPA): `@DataJpaTest` — EntityManager 주입 확인 (빈 깡통, 미래 entity 작성 가이드 주석)
- 통합: `@SpringBootTest(RANDOM_PORT)` + `@Testcontainers` — MySQL 8.4 + Redis 7.4-alpine 컨테이너, `/api/health` 200 검증
- 통합 테스트는 기본 skip (`-DskipITs=true`), `-Dtourdoum.it=true` 또는 `-DskipITs=false`로 활성화
- JaCoCo 리포트 생성만 (임계 0%)

## 리스크 / 미해결

- Maven wrapper 생성: `mvn wrapper:wrapper` 는 현재 JDK17이 기본으로 잡혀 있어 JAVA_HOME 환경변수를 명시해야 함.
- Testcontainers 통합 테스트는 Docker 실행 환경 필요. 기본 skip 처리로 CI-less 환경 대응.
- `spring.jpa.hibernate.ddl-auto=validate`는 스키마 없이 구동 시 실패 → dev profile에서만 `update`로 오버라이드.
- QueryDSL APT: Maven compile 단계에서 Q클래스 생성. 현재 entity가 없어 빈 `target/generated-sources/annotations` 만 생성됨.
