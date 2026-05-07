# UI Round 2 Brainstorm — Codex 회의

## 컨텍스트

TourDoum 프론트엔드 (Vue 3 + Vite + Tailwind CSS + shadcn-vue 패턴).
Round 1 완료: prefers-reduced-motion / Select 컴포넌트 / Active nav.

## 현황 감사 결과

### 하드코딩 색상 (다크모드 위험)
- `HomeView.vue:80` — `bg-green-50 text-green-700 bg-green-500` (백엔드 health 상태 배지)
- `MeView.vue:216` — `bg-green-50 dark:bg-green-950/30 border-green-200 dark:border-green-900 text-green-700 dark:text-green-400` (성공 알림 박스)
- `MeView.vue:277` — `text-green-600 dark:text-green-400` (비밀번호 확인 일치 텍스트)

### Skeleton 인라인 animate-pulse
- `AccommodationsView.vue:62` — `<Card class="animate-pulse">` + 내부 `<div class="h-4 bg-muted">` 패턴
  → `<Skeleton>` 컴포넌트(`components/ui/skeleton/`)가 이미 존재하나 미사용

### Toast 미존재
- 즐겨찾기 토글, 예약 완료, 계획 저장 등 피드백 없음
- `components/ui/` 하위에 toast/ 없음

### 기존 CSS 변수 (index.css)
현재 존재: `--primary`, `--secondary`, `--destructive`, `--muted`, `--accent`, `--border`, `--input`, `--ring`, `--background`, `--foreground`
**없음**: `--success`, `--warning`, `--info` 시맨틱 토큰

## 질문 (Codex에게)

1. **시맨틱 토큰 설계**: `--success` / `--warning` / `--info` CSS 변수를 index.css에 추가할 때, Tailwind `@layer base` 안에 `:root` + `.dark` 양쪽 값 제안해줘. shadcn-vue 패턴(hsl 값, oklch 아님)으로. 라이트/다크 각각 성공=green, 경고=amber, 정보=sky 계열.

2. **HomeView 배지 교체**: 위 토큰을 활용해 `bg-green-50 text-green-700` 하드코딩을 `bg-success/10 text-success` 같은 시맨틱 클래스로 대체하는 패턴 보여줘. Tailwind 커스텀 토큰 연결 방법 포함.

3. **Skeleton 교체 패턴**: AccommodationsView의 `<Card class="animate-pulse">` 블록을 `<Skeleton>` 컴포넌트로 교체할 때 가장 DRY한 패턴은? 카드 레이아웃 유지 조건.

4. **Toast 설계 간단 제안**: shadcn-vue 패턴으로 Toast 컴포넌트를 만든다면 최소 API는? `useToast()` composable + `<Toaster>` 전역 마운트 패턴. 구현 난이도 평가.

Round 2에서 D(시맨틱 토큰+HomeView), E(Toast), F(Skeleton) 중 어느 것을 먼저 하면 가장 효과적인지 우선순위도 의견 줘.
