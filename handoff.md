# handoff — be/feat-bootstrap

작성자: Implementer-A (BE) / 날짜: 2026-05-06

## 완료 항목

- `backend/` Spring Boot 3.3.5 + JDK 21 Maven 프로젝트 생성
- `GET /api/health` → `{"status":"UP","app":"tourdoum"}` 구현
- 시드 테스트 3종: HealthControllerTest, JpaSliceSampleTest, HealthIntegrationTest
- `./mvnw verify -DskipITs=false` → BUILD SUCCESS (3/3 pass)
- Maven Wrapper, .env.example, checkstyle-suppressions.xml, backend README

## 결정이 필요한 항목 (Architect → 사용자 확인)

1. **Checkstyle 설정 강화 여부**: 현재 google_checks.xml + failOnViolation=false. 향후 Javadoc 규칙 제외 suppressions 추가 여부.
2. **H2Dialect 명시 제거**: `src/test/resources/application.yml` 에서 H2Dialect 명시를 제거해도 되는지(Hibernate 자동 감지).
3. **통합 테스트 기본 활성화 시점**: 현재 `-DskipITs=true` 기본. Jenkins 파이프라인 구성 후 기본 활성화로 전환할 시점.

## 미해결 질문

- `TOUR_API_KEY` 발급 예정 시점? (현재 `.env.example` 에 자리만 잡음, 코드 없음)
- MySQL 공간 인덱스(`POINT` + `ST_Distance_Sphere`) 스키마 적용은 별도 브랜치/ADR 예정?
- Redis 세션 직렬화 전략 확정 필요 (기본 JDK → Jackson 또는 Kryo 선호 여부)

## 다음 worktree 추천 슬러그

- `be/feat-domain-user` — 회원(Member) Entity + Repository + 폼 로그인 세션 (Spring Security 적용)
- `be/feat-domain-attraction` — 여행지(Attraction) Entity + MySQL 공간 인덱스 + QueryDSL 동적 쿼리

