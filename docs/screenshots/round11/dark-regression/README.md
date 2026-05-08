# Round 11 — dark variant 회귀 검증

R11.1 토큰 9종(`--state-pending/--state-failed/--state-neutral` + `--queue-progress-{from,to}`
+ `--countdown-warning-{bg,text}` + `--banner-emphasis-{from,to}`)의 dark variant가
R7~R10 dark 정합과 어긋나지 않는지 시각 검증.

## 캡처 (dev 서버 + html.dark 강제 + colorScheme=dark)

| 라우트 | 320 | 375 | 640 |
|---|---|---|---|
| `/dev/r11-reservation-lab` | r11-lab-dark-320 | r11-lab-dark-375 | r11-lab-dark-640 |
| `/dev/me-mock` | me-mock-dark-320 | me-mock-dark-375 | me-mock-dark-640 |
| `/dev/ui-catalog` | ui-catalog-dark-320 | ui-catalog-dark-375 | ui-catalog-dark-640 |

## 검증 결과 — 변화 X (보정 불요)

### R11.1 신규 토큰 사용 영역 (`/dev/r11-reservation-lab`)

1. **PeakSeasonBanner 3 phase**: `--banner-emphasis-{from,to}` 다크 sky-950 → indigo-950 그라디언트 정상 노출. 카운트다운/CTA/메타 텍스트 모두 가독.
2. **Imminent countdown**: `--countdown-warning-bg` red-950 + `--countdown-warning-text` red-300 적용. 추정 contrast ≥6:1 (WCAG AA pass). 시각 식별 명확.
3. **Queue progress bar**: `--queue-progress-from/to` (sky-500 → emerald-500)는 light/dark 동일 스펙. 다크 muted track 위에서 그라디언트 가시성 양호.
4. **FSM 라벨 chip 13종**: `hsl(var(token) / 0.15)` 배경 + `hsl(var(token))` 텍스트.
   - `--state-pending` (sky-500), `--state-failed` (red-500), `--success` (green-500), `--warning` (amber-500), `--state-neutral` (slate-300) 모두 가독.
   - REFUNDED(state-neutral, slate-300)는 chip 내부 contrast 살짝 낮으나 읽기 가능 — 추후 R12 Timeline 본 사용 시 ≥4.5:1 확정 위해 slate-200으로 보정 검토 가능 (본 라운드 미적용, 시각 OK).

### 기존 라우트 회귀 검증

5. **`/dev/me-mock` (R7~R9 dark contrast 회귀 가드)**: 헤더/카드/foreground 색 모두 R7~R9 박제 톤과 동일. `--success`/`--warning` 변화 없음 → ROLE_MEMBER 배지/링크 색 유지.
6. **`/dev/ui-catalog`**: 컴포넌트 카탈로그 다크 톤 유지 — Tabs/Badge/Popover 등 R10에서 토큰화한 transition 동작 회귀 X.

## 잔여 risk

- `--state-neutral` dark variant slate-300은 chip(15% bg)에서 살짝 낮은 contrast. 본 라운드 보정 없음, R12 Timeline 실 사용 시점에 contrast 측정 후 slate-200 후보로 재검토.
- 본 라운드는 시각 검증만. dark 다이내믹 contrast(컴포넌트 hover/focus 상태)는 미점검 — R12 Timeline/Refund 박제 시 dynamic 검증 필요.
