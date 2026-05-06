# handoff.md — fe/feat-bootstrap

갱신: 2026-05-06 | 에이전트: Implementer-B (FE) → Reviewer / Architect

## 완료 요약

`frontend/` 디렉터리를 수동 스캐폴드로 생성했다. Vue 3 + Vite + TypeScript + Pinia + vue-router + Biome 조합. 헬스체크 홈 화면, 시드 단위 테스트(2개), e2e 뼈대 포함.

## 결정이 필요한 항목

| # | 항목 | 현재 상태 | 제안 |
|---|---|---|---|
| 1 | 카카오 지도 키 | index.html 주석 처리 | 키 발급 후 `.env.local`에 설정하고 주석 해제 |
| 2 | e2e 실행 환경 | playwright.config.ts 설정 완료, 실제 실행은 CI 전용 | Jenkins pipeline에 `npm run test:e2e` 단계 추가 |
| 3 | `@vue/tsconfig` 미설치 | TypeScript ~5.5와 호환 버전 없어 수동 inline | TypeScript 업그레이드 시 `@vue/tsconfig` 복귀 가능 |
| 4 | pre-commit 훅 | frontend 내 설정 없음 | 프로젝트 루트에서 `pre-commit install` (infra 담당) |

## 미해결 질문

- BE 헬스체크 응답 구조: `{ "status": "UP" }` 형태 가정. BE agent 확인 필요.
- `VITE_API_BASE_URL` 운영 환경 값: docker-compose 내 서비스명 기반인지 확인 필요.
- 커버리지 리포트 생성: 현재 없음. vitest의 `--coverage` 플래그 + `@vitest/coverage-v8` 추가 필요 시 Architect 지시 필요.

## 다음 단계 제안

1. **Architect**: fe/feat-bootstrap → develop PR 생성 및 머지 판정
2. **infra worktree (슬러그: `infra-feat-bootstrap`)**: docker-compose.yml, Jenkinsfile, pre-commit 설정
3. **FE 다음 기능 worktree (슬러그: `fe-feat-tour-search`)**: 여행지 검색 화면 — Architect 지시 후 시작

## 파일 트리

```
frontend/
├── .env.example
├── .gitignore
├── README.md
├── biome.json
├── e2e/
│   └── home.spec.ts           ← Playwright 시드 e2e
├── index.html                 ← 카카오 SDK 주석 처리
├── package.json
├── playwright.config.ts
├── src/
│   ├── App.vue                ← TourDoum 타이틀 + RouterView
│   ├── api/
│   │   └── client.ts          ← fetch 래퍼
│   ├── main.ts
│   ├── router/
│   │   └── index.ts           ← / → HomeView
│   ├── stores/
│   │   └── health.ts          ← Pinia fetchHealth() 스토어
│   └── views/
│       ├── HomeView.vue       ← 헬스체크 표시
│       └── __tests__/
│           └── HomeView.spec.ts  ← Vitest + MSW 시드 테스트 (2 tests)
├── tsconfig.app.json
├── tsconfig.json
├── tsconfig.node.json
└── vite.config.ts
```

