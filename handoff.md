# handoff — ui/feat-mockups-polish

**커밋:** `9bedac1`
**브랜치:** `ui/feat-mockups-polish` → `develop` 머지 대상

## 완료 항목

| 항목 | 상태 |
|------|------|
| Badge.vue (6 variant CVA) | ✅ |
| Avatar.vue (이니셜, size/variant prop) | ✅ |
| AttractionsView 폴리싱 | ✅ |
| AttractionDetailView 폴리싱 | ✅ |
| AccommodationsView 폴리싱 | ✅ |
| AccommodationDetailView 폴리싱 | ✅ |
| FavoritesView 폴리싱 | ✅ |
| ChatView 폴리싱 | ✅ |
| ChatChannelView 폴리싱 | ✅ |
| DMView 폴리싱 | ✅ |
| vitest 19/19 pass | ✅ |
| vue-tsc --noEmit 0 errors | ✅ |
| agent-finalize build smoke | ✅ |

## 결정이 필요한 항목

- **Badge `sky`/`emerald` variant**: CVA로 Tailwind static 클래스 사용 중.
  purge 이슈 없도록 `tailwind.config.js` `content` 패턴에 `src/components/**/*.vue` 포함 여부 확인 권장.
- **Avatar 실제 이미지 지원**: 현재 이니셜 텍스트만. 백엔드가 프로필 이미지 URL을 반환하면 `src` prop 추가 필요.

## 미해결 질문

- AccommodationDetailView 예약 버튼: 현재 "(mockup)" 표시. BE 예약 API 연결 시 Button 동작 구현 필요 (Task별도).
- DMView `msg.senderId === 0` 하드코딩: 로그인 사용자 ID를 스토어에서 가져오도록 추후 교체 권장.

## 다음 단계 제안

1. Reviewer가 `develop` 기준 PR 생성 후 이중 리뷰
2. 머지 후 FE가 실제 지도 API / 예약 API 연결 시 스타일 토큰 유지 확인
3. Storybook 또는 간단한 컴포넌트 쇼케이스 페이지 추가 고려 (Badge/Avatar)
