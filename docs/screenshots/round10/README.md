# Round 10 — transition 토큰화 audit + 미캡처 라우트 부분 캡처

R7~R9에서 Button/Input/Select/Sheet/AlertDialog/Toast/SearchWidget을 motion
토큰(`--motion-fast/base`, `--ease-standard/emphasized`)으로 일원화. R10은
잔존 hardcoded transition 3곳(Tabs/Badge/Popover) 치환 + viewport 누락분
보강.

## 캡처

| 파일 | 의도 | viewport |
|---|---|---|
| `ui-catalog-tabs-badge-375.png` | Tabs/Badge 토큰 치환 후 시각 회귀 evidence | 375 |
| `me-mock-320.png` | `/dev/me-mock` 최소 viewport 렌더 — MeView 풀 표시 | 320 |
| `me-mock-375.png` | 표준 viewport (R9 light 캡처와 동치 확인) | 375 |
| `me-mock-640.png` | 대형 viewport — 레이아웃 max-width 회귀 확인 | 640 |

## 토큰 치환 대상

- `Tabs.vue:93` — `transition-colors`(tailwind 기본 150ms) → `transition-[color,background-color,border-color] duration-[var(--motion-fast)] ease-[var(--ease-standard)]`
- `Badge.vue:6` — 동일 패턴 치환.
- `Popover.vue:124-125` — `duration-150 ease-out` / `duration-100 ease-in` (R7 토큰 정의 이전 잔존) → `duration-[var(--motion-fast)] ease-[var(--ease-standard)]` 양방 대칭.
- `SearchWidget.vue:74-75` — `duration-200/150 ease-out/in` → `--motion-base/--motion-fast` + `--ease-standard` (audit 중 추가 발견분).

## 기각/이연

- 후보 1 (Sheet panel scale-in): translateX 기반 side drawer라 scale-in 동시 적용 시 행렬 충돌 + UX 의도(가장자리 슬라이드) 흐려짐 → 미적용. AlertDialog는 중앙 정렬이라 R9 scale-in이 자연스러웠던 것.
- 후보 3 (채팅/예약/플랜 라우트 캡처): BE 부팅 + seed 의존 — `dev seed route` 신설 vs `BE+seed runner` 활용 결정이 선행. `handoff.md`에 인계.
- 후보 4 (dev seed route 결정): scope crawl 회피, Architect 결정 대상으로 인계.
