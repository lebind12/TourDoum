# plan — ui/feat-mockups-polish

**역할:** UI/UX Designer | **브랜치:** `ui/feat-mockups-polish`

## 목표

Task #4(FE 스켈레톤)에서 생성된 8개 도메인 뷰를 디자인 시스템에 수렴시킨다.
- 하드코딩 색상(text-slate-*, bg-sky-*, bg-white) → CSS 변수 토큰
- 날 Tailwind div → shadcn 컴포넌트 (Card, Badge, Button, Input, Avatar)
- inline SVG → lucide-vue-next 아이콘
- aria-label / role 접근성 속성 추가
- 스크립트 셋업 로직·라우트·스토어 **무변경**

## 스펙 출처

- Task #5 JSON 명세 (team-lead 발행)
- `frontend/src/index.css` CSS 변수 정의
- 기존 shadcn 컴포넌트: Button, Card, Input, Label

## 변경 후보 파일

신규:
- `frontend/src/components/ui/badge/Badge.vue` + `index.ts`
- `frontend/src/components/ui/avatar/Avatar.vue` + `index.ts`

수정:
- `frontend/src/views/AttractionsView.vue`
- `frontend/src/views/AttractionDetailView.vue`
- `frontend/src/views/AccommodationsView.vue`
- `frontend/src/views/AccommodationDetailView.vue`
- `frontend/src/views/FavoritesView.vue`
- `frontend/src/views/ChatView.vue`
- `frontend/src/views/ChatChannelView.vue`
- `frontend/src/views/DMView.vue`

## 테스트 전략

- 단위: vitest 기존 19개 (스크립트 로직 무변경 → 신규 테스트 불필요)
- 통합: 없음 (UI 전용)
- e2e: 해당 없음

## 리스크 / 미해결

- biome 버전 불일치(1.8.3 hook vs 1.9.x local): pre-commit 자동 수정 → staged 재적용 후 재커밋
- shadcn-vue CLI 사용 불가(components.json 스키마 불일치): 수동 CVA 작성으로 우회
