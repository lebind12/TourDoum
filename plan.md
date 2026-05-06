# plan.md — Implementer-B (FE) / fe/feat-bootstrap

- 작성: 2026-05-06
- 에이전트: Implementer-B (FE), Sonnet 4.6
- worktree: 20-spec-tourdoum-fe-feat-bootstrap
- 기반 브랜치: develop

## 목표

`frontend/` 디렉터리를 **수동 스캐폴드**로 생성한다. 도메인 화면(여행지 검색, 숙박 목록)은 이번 범위 밖. 부트스트랩 + 헬스체크 홈페이지 + 시드 테스트 1세트가 전부다.

## 스펙 출처

- `20-spec-tourdoum/docs/adr/0001-bootstrap.md`
- Implementer-B 작업 지시 (사용자 메시지)
- CLAUDE.md §9 운영 원칙

## 변경 후보 파일

```
frontend/
  package.json
  vite.config.ts
  tsconfig.json
  tsconfig.node.json
  index.html
  biome.json
  .env.example
  .gitignore
  README.md
  playwright.config.ts
  src/
    main.ts
    App.vue
    router/index.ts
    stores/health.ts
    api/client.ts
    views/HomeView.vue
    views/__tests__/HomeView.spec.ts
  e2e/
    home.spec.ts
```

## 기술 스택

| 항목 | 선택 | 근거 |
|---|---|---|
| Vue 3 + Vite + TypeScript | ADR-0001 확정 | - |
| Pinia | 상태 관리 | ADR-0001 |
| vue-router v4 | SPA 라우팅 | ADR-0001 |
| Vitest + Vue Test Utils + MSW | 단위/컴포넌트 테스트 | ADR-0001 |
| Playwright | e2e | ADR-0001 |
| Biome | lint/format | ADR-0001 (eslint/prettier 미사용) |

## 테스트 전략

- 단위: `HomeView.spec.ts` — MSW로 `/api/health` 모킹 → "UP" 텍스트 노출 검증
- 통합: 해당 없음 (FE 전담)
- e2e: `e2e/home.spec.ts` — "TourDoum" 텍스트 확인. 백엔드 필요 → CI 전용, README 명시
- 커버리지 임계 = 0% (ADR-0001 학습 친화 모드)
- **신규 테스트 양산 금지** — 시드 1세트만 생성 (ADR-0001 §학습 친화 모드)

## 리스크 / 미해결

- `npm install` 네트워크 실패 → `handoff.md`에 명시 후 종료 (강제 우회 금지)
- Biome: `.vue` 파일 분석 일부 제한 가능 (허용 가능, `.ts` 기준으로 lint)
- Playwright e2e는 백엔드 의존 → 로컬 실행 시 스킵 처리
- 카카오 지도 키 미발급 → `index.html`에 주석 처리

