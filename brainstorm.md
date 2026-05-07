# Design System Brainstorm — Round 1

날짜: 2026-05-07
브랜치: `ui/polish-ds-round1`

---

## 현황 감사 (코드 기반)

### 공통 CSS (`index.css`)
- `--primary` sky-600 ✅, `--destructive` ✅
- `--success` / `--warning` / `--info` 시맨틱 토큰 **없음** → 뷰에서 `green-50 text-green-700` 하드코딩
- `prefers-reduced-motion` 미디어 쿼리 **없음** → wiggle/pulse/transition 모션 민감 사용자에게 그대로 재생

### 컴포넌트
- `Input`, `Button`, `Badge`, `Card`, `Skeleton`, `Popover` ✅ (일관된 패턴)
- **`Select` 컴포넌트 없음** → AttractionsView, AccommodationsView, PlansView에서 동일한 80자 클래스 문자열을 3회 반복
- `Toast/Snackbar` 없음 — 즐겨찾기 토글, 예약 완료 등 피드백 없음

### AppShell
- 현재 활성 메뉴 링크에 **시각적 표시 없음** → 사용자가 어느 섹션에 있는지 인지 불가
- 모바일 드로어에도 동일한 문제

### 뷰별 문제
| 화면 | 문제 |
|------|------|
| HomeView | `bg-green-50 text-green-700` 하드코딩 (다크모드 깨짐) |
| AttractionsView | 3개 `<select>` 원시 HTML, 클래스 반복 |
| AccommodationsView | 2개 `<select>` 원시 HTML, animate-pulse 인라인 (Skeleton 미사용) |
| PlansView | `<select>` 원시 HTML |
| 전체 | prefers-reduced-motion 미적용 |

---

## 개선 후보 (가치 / 난이도)

| # | 항목 | 가치 | 난이도 | Round |
|---|------|------|--------|-------|
| A | `prefers-reduced-motion` CSS | ★★★ a11y | 낮음 | **1** |
| B | `Select` base 컴포넌트 + 뷰 교체 | ★★★ DRY | 중간 | **1** |
| C | Active nav indicator (AppShell) | ★★★ UX | 낮음 | **1** |
| D | 시맨틱 토큰 (success/warning) + HomeView 수정 | ★★ DM | 중간 | 2 |
| E | `Toast` 컴포넌트 (즐겨찾기/저장 피드백) | ★★★ UX | 높음 | 2 |
| F | AccommodationsView Skeleton 컴포넌트 교체 | ★★ DRY | 낮음 | 2 |
| G | 컴포넌트 카탈로그 `/dev/ui-catalog` | ★★ DX | 높음 | 3 |
| H | 반응형 점검 (< 640px) | ★★ 반응형 | 중간 | 3 |

---

## Round 1 픽업 (3건)

### A. `prefers-reduced-motion` — CSS 한 블록
- `index.css` 에 `@media (prefers-reduced-motion: reduce)` 추가
- wiggle, animate-pulse, transition 전부 덮음
- 변경 파일: `frontend/src/index.css`

### B. `Select` base 컴포넌트
- `frontend/src/components/ui/select/Select.vue` 신규 (Input과 동일 패턴)
- `frontend/src/components/ui/select/index.ts`
- AttractionsView (3개), AccommodationsView (2개) 교체
- 변경 파일: 2 view + 2 신규

### C. Active nav 인디케이터
- `AppShell.vue` script setup에 `useRoute` 추가
- 활성 경로에 `text-primary bg-primary/5` 클래스 조건부 적용 (데스크탑 nav + 모바일 드로어)
- 변경 파일: `AppShell.vue`

---

## 리스크
- Select 교체: v-model 바인딩이 `@change` 이벤트를 중복으로 사용. Select 내부에서 `emit('update:modelValue')` 하면 외부 `@change`는 store 호출용. 충돌 없음 확인 필요.
- Active nav: `useRoute()` 추가 (script setup 최소 변경, 도메인 로직 없음, UI 범위 내).
