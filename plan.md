# plan — fe/feat-auth-ui

## 목표

TourDoum 프론트엔드에 회원가입·로그인·로그아웃·인증 가드를 구현한다.
BE가 노출하는 세션 기반 인증 엔드포인트를 소비하는 Pinia 스토어와 Vue 뷰를 추가한다.

## 스펙 출처

- 작업 지시문 (BE 엔드포인트: POST /api/members/signup, POST /api/auth/login, POST /api/auth/logout, GET /api/me)
- docs/adr/0003-*.md (세션 기반 인증, CORS credentials=true)

## 변경 후보 파일

- `frontend/src/api/client.ts` — credentials: 'include' 기본화, POST/JSON 지원
- `frontend/src/stores/auth.ts` — 신규: currentUser, signup/login/logout/fetchMe 액션
- `frontend/src/router/index.ts` — /login, /signup, /me 라우트 + beforeEach 가드
- `frontend/src/views/LoginView.vue` — 신규
- `frontend/src/views/SignupView.vue` — 신규
- `frontend/src/views/MeView.vue` — 신규
- `frontend/src/views/HomeView.vue` — currentUser 표시 + 로그아웃 버튼
- `frontend/src/views/__tests__/LoginView.spec.ts` — 신규 (단위 1개)
- `frontend/e2e/auth.spec.ts` — 신규 (e2e 1개, E2E_BACKEND=1 환경변수 시에만)
- `frontend/.env.example` — VITE_API_BASE_URL 확인/추가

## 테스트 전략

- 단위: LoginView.spec.ts — MSW로 /api/auth/login 모킹, 성공 케이스 1개
- e2e: auth.spec.ts — E2E_BACKEND=1 환경변수 시에만 실행. 실제 BE 호출.
  시나리오: 회원가입 → 자동 로그인 → 홈 닉네임 확인 → 로그아웃

## 리스크 / 미해결

- BE 미가동 환경에서 e2e 스킵 로직 필요 → playwright.config testIgnore 조건부 처리
- 세션 쿠키(SameSite, Secure) 설정은 BE와 협의 필요
- 자동 로그인(회원가입 직후) — BE가 signup 후 세션을 자동 발급하는지 확인 필요.
  미발급 시 signup 성공 후 login 액션을 별도 호출.
