# plan — QA 첫 dispatch: 회귀 + e2e

**역할:** QA / Test Runner | **브랜치:** `qa/feat-regression-e2e`

## 목표

develop 브랜치의 누적 머지(#1·#2·#3, Mockup+폴리싱, nearby distance/IT)에 대해:
- 회귀 테스트 실행 (BE unit/integration, FE unit)
- 도메인횡단 e2e 2개 시나리오 검증
- 발견 사항 정리 및 Architect 결정 필요 항목 보고

## 스펙 출처

- Task #2: QA 첫 dispatch 할당
- CLAUDE.md §3: QA 역할 = 회귀·e2e·CI 트러블슈팅
- 현재 브랜치: develop (HEAD: cf7a0b8)

## 테스트 전략

### 1. Backend 회귀 (mvnw verify)
```bash
mvnw -DskipITs=false -Dtourdoum.it=true verify
```
- 단위 테스트 + 통합 테스트 전체 실행
- 깨진 케이스 식별 → 별도 task로 보고 (기능 코드 수정 금지)

### 2. Frontend 빌드 + 테스트
```bash
npm run build
vitest --run
```
- 25개 unit tests 실행
- 커버리지 확인

### 3. Playwright e2e (E2E_BACKEND=1)
```bash
npm run e2e
```
- BE + FE 동시 실행 환경에서 e2e 테스트 실행

### 4. 도메인횡단 e2e 시나리오
**Scenario A**: 회원가입 → 로그인 → /me → /attractions → /favorites → 로그아웃
- auth guard 통과 확인

**Scenario B**: 비로그인 /favorites 접근 → 리다이렉트 → 로그인 → 자동 복귀
- 보호된 라우트 동작

## 변경 파일 (테스트 코드만, 기능 코드 X)

- `backend/src/test/**/*`: 필요 시 회귀 테스트 추가
- `frontend/src/**/__tests__/**/*`: vitest 체크
- `e2e/**/*`: Playwright 시나리오 추가

## 리스크

- E2E_BACKEND 환경 미설정: 즉시 중단 + Architect 보고
- 외부 API/DB 연결 실패: silent fallback 금지
- 테스트 자체 버그: handoff.md에 명시

## 산출물

1. `self-review.md`: 각 단계별 결과 + harness check 게이트 표
2. `handoff.md`: 발견된 회귀/이슈, 미해결 사항
3. 필요 시 회귀 테스트 코드 (테스트만)
