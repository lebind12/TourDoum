# handoff — fe/feat-auth-ui

## 결정이 필요한 항목

1. **회원가입 후 자동 로그인 방식**
   - 현재: `signup()` 성공 후 `login()` 액션을 연달아 호출 (BE 세션 자동 발급 불확실 가정)
   - BE가 signup 응답에 세션을 자동 발급하면 추가 login 호출이 중복됨 → BE 확인 필요
   - ADR 추가 또는 BE 담당자 확인 요망

2. **`/api/me` 응답 스키마 확인**
   - `MeView`는 `{ id, email, nickname, role, createdAt? }` 구조를 가정
   - BE 실제 응답 필드와 맞는지 통합 테스트 시 확인 필요

3. **라우터 가드 중복 fetchMe 방지**
   - 현재: `currentUser === null && !loading` 조건으로 중복 방지
   - SPA 내 첫 진입 시에만 한 번 호출되도록 `initialized` 플래그 추가 고려

4. **HomeView 기존 단위 테스트 Vue warn**
   - HomeView.spec.ts가 router를 주입하지 않아 RouterLink warn 발생
   - 테스트 통과는 하나, 기존 테스트 개선 필요 여부는 QA/Reviewer 판단 위임

## 미해결 질문

- BE의 `POST /api/auth/logout` 응답 형식 (204 vs 200 JSON)?
  현재 204로 가정하여 `post<null>()` 처리. 200+JSON이면 타입 변경 필요.
- 세션 쿠키 SameSite 설정 — 개발(localhost) 환경에서 `credentials: 'include'` 작동 확인 필요

## 다음 단계 제안

1. **`be/feat-auth-session`** (또는 기존 BE worktree): 위 1~2 항목 확인 후 BE-FE 통합 연동 테스트
2. **`fe/feat-travel-search`**: 여행지 검색 화면 (인증 완료 후 다음 핵심 기능)
