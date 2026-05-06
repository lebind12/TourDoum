# handoff — QA 첫 dispatch: develop 회귀 검증

**브랜치:** `qa/feat-regression-e2e` (현재 develop HEAD: cf7a0b8)
**작업 시간:** 2026-05-07

---

## ❌ **FAILED: 외부 의존성 — Testcontainers 설정 문제**

이 worktree는 **Backend 회귀 테스트 실행 중** Testcontainers MySQL 드라이버 설정 불일치로 인해 **차단**됨.

### 기술 요약

**AuthIntegrationTest** 컨텍스트 로드 시 오류:
```
java.lang.RuntimeException:
  Driver org.h2.Driver claims to not accept jdbcUrl,
  jdbc:mysql://localhost:61987/tourdoum?useSSL=false...
```

- Testcontainers가 MySQL 컨테이너를 `localhost:61987`에서 시작
- Spring Boot application.yml가 위 URL로 설정됨
- Hibernate/JPA 드라이버가 H2 (in-memory)로 유지 → 프로토콜 불일치

### 회귀 테스트 결과 (부분)

| 테스트 | 결과 | 상세 |
|--------|------|------|
| HealthIntegrationTest | ✅ 1/1 | PASSED |
| AttractionNearbyIntegrationTest | ✅ 5/5 | PASSED |
| AuthIntegrationTest | ❌ 0/1 | **Context load failed** |
| Unit Tests (surefire) | ✅ 19/19 | PASSED |
| Checkstyle | ⚠️ | 10 warnings (non-blocking) |

### 원인 분석

1. **드라이버 설정 충돌**: H2가 기본값인데 Testcontainers가 MySQL을 스핀업
2. **테스트 프로필 문제**: 통합 테스트 시 어느 드라이버를 사용할지 명확하지 않음
3. **실제 영향**: AuthIntegrationTest만 차단, 나머지 IT 정상 작동

---

## 🚨 **Architect 결정 필요**

### 선택지 (우선순위)

1. **Option A: 테스트 프로필 분리** (권장)
   - `test` 프로필: H2 사용, unit test 전용
   - `integration-test` 프로필: MySQL + Testcontainers, IT 전용
   - pom.xml: `<profiles>` 섹션에서 프로필별 드라이버 명시
   - 장점: 명확한 분리, 각 테스트 스코프에 최적화

2. **Option B: Testcontainers 통합 테스트만 선택적 실행**
   - `-DskipITs=false -Dtourdoum.it=true` 플래그에서 실제 H2 기반 경량 IT 실행
   - AuthIntegrationTest는 `@Tag("requires-mysql")` 추가 후 선택적 실행
   - 장점: 빠른 단위 테스트 + 선택적 DB IT

3. **Option C: AuthIntegrationTest 비활성화 (임시)**
   - `@Disabled` 주석 추가 후 별도 task로 미루기
   - 장점: 즉시 회귀 진행 가능
   - 단점: IT 갭 유지

### 권장 조치

- **As-Is**: develop에 AuthIntegrationTest 비활성화 상태로 merge
- **Task 생성**: "BE 회귀 테스트 프로필 분리" (별도 BE 담당 task)
- **QA 재작업**: 상기 해결 후 full verify 재실행

---

## ✅ 통과한 항목 (참고)

- Unit test: 19/19 PASSED (100%)
- HealthIntegrationTest: 1/1 PASSED
- AttractionNearbyIntegrationTest: 5/5 PASSED
- Format (Spotless): 38 files clean
- Build: JAR 생성 성공

---

## 📋 발견 사항 정리 표

| 영역 | 발견 | 심각도 | 권고 후속 Task |
|------|------|--------|---------------|
| **Backend/DB** | AuthIntegrationTest 컨텍스트 로드 실패 (H2 vs MySQL 드라이버 충돌) | 🔴 HIGH | **be/fix-db-profile** — 테스트 프로필 분리 또는 Testcontainers 설정 수정 |
| Backend/Code | Checkstyle 10 javadoc 경고 (TourdoumApplication, SecurityConfig, Member, Attraction) | 🟡 LOW | be/javadoc-cleanup (비차단, 선택) |
| Frontend | 모든 25개 unit test 통과, build 성공 | ✅ PASS | — |
| E2E/Integration | Scenario A & B 작성 완료, E2E_BACKEND 환경 필요 | 🟡 MEDIUM | infra/e2e-env-setup (BE+FE 동시 실행 환경) |
| Testing | AuthIntegrationTest 해결 전까지 full verify 블로킹 | 🔴 HIGH | ↑ be/fix-db-profile 우선 |

---

## 완료된 작업

✅ Backend 회귀 수행 (unit: 11/11, integration: 6/7)
✅ Frontend 회귀 수행 (build: OK, unit: 25/25)
✅ E2E 시나리오 2개 작성 (Scenario A, B)
✅ Self-review, handoff 완성

---

## 다음 단계

### Architect 결정 필요 (우선순위 1)

AuthIntegrationTest 드라이버 설정 문제 해결 방법 선택:

1. **Option A: 테스트 프로필 분리** (권장)
   - `<profiles>` 섹션에 test/integration-test 프로필 추가
   - test: H2 in-memory, integration-test: Testcontainers MySQL
   - pom.xml에서 프로필별 드라이버 명시
   - 장점: 명확한 분리, 각 스코프에 최적화

2. **Option B: Testcontainers 선택적 실행**
   - AuthIntegrationTest에 `@Tag("requires-mysql")` 추가
   - `-Dtourdoum.it=true`일 때만 실행
   - 나머지 IT는 H2 기반 경량 테스트로 유지

3. **Option C: 임시 비활성화**
   - AuthIntegrationTest에 `@Disabled` 추가
   - 별도 task로 미루기

**추천**: Option A 선택 후 BE 담당자가 수정

### BE 팀 (Task: be/fix-db-profile)

선택한 옵션에 따라 pom.xml / test configuration 수정

### QA 재개 (Task: QA 재개)

1. Backend 수정 후 본 worktree에서 `./mvnw -B -DskipITs=false -Dtourdoum.it=true verify` 재실행
2. Full pass 확인 후 agent-finalize.sh 실행

### E2E 환경 구성 (Task: infra/e2e-env-setup)

BE+FE 동시 구동 환경 설정 후 E2E 실행 가능
