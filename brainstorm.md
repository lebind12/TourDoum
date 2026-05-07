# Design System Brainstorm — Round 2

날짜: 2026-05-07
브랜치: `ui/polish-ds-round2`

---

## Codex 회의록

> **시도**: `codex-call.sh` 실행 → `OPENAI_API_KEY not set in environment`
> codex CLI는 PATH에 존재하나 이 세션의 환경변수에 키가 노출되지 않음.
> 사전 허가 조건(Codex 장애 시 건너뛰기)과 동일 처리 — 자체 분석으로 진행.

---

## 현황 감사 (코드 기반)

### 하드코딩 색상 (다크모드 위험)
| 파일 | 라인 | 코드 |
|------|------|------|
| `HomeView.vue` | 80-81 | `bg-green-50 text-green-700 bg-green-500` (백엔드 health 배지) |
| `MeView.vue` | 216 | `bg-green-50 dark:bg-green-950/30 border-green-200 dark:border-green-900 text-green-700 dark:text-green-400` (성공 알림 박스) |
| `MeView.vue` | 277 | `text-green-600 dark:text-green-400` (비번 확인 일치) |

### Skeleton 인라인 animate-pulse
- `AccommodationsView.vue:62` — `<Card class="animate-pulse">` + `<div class="h-4 bg-muted">` 수동 패턴
- `components/ui/skeleton/` 이미 존재하나 미사용

### Toast 미존재
- 즐겨찾기, 예약, 계획 저장 등 user action 피드백 없음
- `components/ui/toast/` 없음 → 신규 구현 필요

---

## 자체 분석 — Round 2 픽업 결정

### D. 시맨틱 토큰 + 하드코딩 제거 (★★★ 가치 / 낮음 난이도)
**계획:**
1. `index.css`에 `:root` + `.dark` CSS 변수 추가
   - `--success` / `--success-foreground` (green)
   - `--warning` / `--warning-foreground` (amber)
2. `tailwind.config.js` (또는 `@theme` 블록)에 `success`, `warning` 컬러 연결
3. `HomeView.vue` 배지: `bg-green-50 text-green-700` → `bg-success/10 text-success`
4. `MeView.vue` 성공 박스 + 비번 확인 텍스트 → 시맨틱 클래스

**리스크**: Tailwind v4는 `@theme` 블록, v3는 `tailwind.config.js`. 프로젝트 버전 확인 필요.

### F. AccommodationsView Skeleton 교체 (★★ 가치 / 낮음 난이도)
**계획:**
- `<Card class="animate-pulse">` 블록 → `<Skeleton>` 컴포넌트 사용 패턴으로 교체
- 카드 레이아웃(h-48 이미지 영역 + 텍스트 라인 2개) 유지

### E. Toast 컴포넌트 (★★★ 가치 / 높음 난이도)
**계획:**
- `components/ui/toast/Toast.vue` — 슬라이드인 알림 (success/error/info variant)
- `composables/useToast.ts` — `toast({ message, variant, duration })` API
- `AppShell.vue`에 `<Toaster />` 글로벌 마운트
- 즐겨찾기 토글, 계획 저장 액션에 연결 (단 store 로직은 건드리지 않음 — UI 범위만)

**우선순위**: D → F → E (D+F 동일 커밋 가능, E는 별도)

---

## Round 2 실행 체크리스트

- [ ] tailwind 버전 확인 (v3 vs v4)
- [ ] D: index.css 시맨틱 토큰 추가
- [ ] D: tailwind.config.ts 커스텀 컬러 연결
- [ ] D: HomeView.vue 배지 교체
- [ ] D: MeView.vue 성공 박스 + 비번 텍스트 교체
- [ ] F: AccommodationsView Skeleton 교체
- [ ] E: Toast 컴포넌트 + useToast composable
- [ ] E: AppShell에 Toaster 마운트
- [ ] lint / test / build
- [ ] commit + push
