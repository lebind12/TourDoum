# TourDoum Frontend

Vue 3 + Vite + TypeScript 기반 프론트엔드.

## 사전 요구

- **Node.js 20 LTS** 이상
- **npm 10** 이상

## 빠른 시작

```bash
# 의존성 설치
npm install

# 개발 서버 실행 (기본 포트 5173)
npm run dev

# 빌드
npm run build

# 빌드 미리보기
npm run preview
```

## 테스트

```bash
# 단위 테스트 (Vitest)
npm test

# 단위 테스트 — 한 번만 실행 후 종료
npm test -- --run

# e2e 테스트 (Playwright) — CI 환경 권장
# 백엔드 없이 실행해도 "TourDoum" 타이틀 확인 시나리오는 통과함
npm run test:e2e
```

> **e2e 안내**: Playwright는 playwright.config.ts의 `webServer` 설정으로 Vite dev server를 자동 기동합니다.
> 백엔드 연동 시나리오가 필요하면 백엔드를 먼저 실행하거나, 워크스페이스의 `infra/docker/docker-compose.yml`을 참조하세요.

## 린트 / 포맷

```bash
# 린트 검사 (Biome)
npm run lint

# 자동 포맷
npm run format
```

## 환경 변수

`.env.example`을 복사해 `.env.local`로 사용합니다.

```
VITE_API_BASE_URL=http://localhost:8080
# VITE_KAKAO_MAP_JS_KEY=   ← 카카오 지도 키 발급 후 입력
```

카카오 지도 SDK는 `index.html`에 주석 처리되어 있습니다. 키 발급 후 해당 `<script>` 태그의 주석을 해제하고 키를 설정하세요.

## 백엔드 / 인프라

- 백엔드: 워크스페이스의 `backend/` 디렉터리 참조
- 로컬 인프라(MySQL, Redis, Jenkins): `infra/docker/docker-compose.yml` 참조

## 디렉터리 구조

```
src/
  main.ts             # 앱 진입점
  App.vue             # 루트 컴포넌트 (TourDoum 타이틀 + RouterView)
  router/
    index.ts          # vue-router 설정 (/ → HomeView)
  stores/
    health.ts         # Pinia 스토어 — 헬스체크 상태
  api/
    client.ts         # fetch 래퍼 (VITE_API_BASE_URL 기반)
  views/
    HomeView.vue      # 홈 화면 — 헬스체크 결과 표시
    __tests__/
      HomeView.spec.ts  # 시드 단위 테스트 (패턴 참고용)
e2e/
  home.spec.ts        # 시드 e2e 테스트 (패턴 참고용)
```
