# Round 11 — 예약 시퀀스 lab + 디자인 토큰 + fixture contract

ADR-0013 Phase 1. R11.1 (토큰) + R11.2 (PeakSeasonBanner) + R11.3 (fixture contract)
까지 박제한 라운드. R11.4~9는 R12+ 분할 task로 이연.

## 캡처

dev 서버 `/dev/r11-reservation-lab` 라우트 — 도메인 store/API import 0.

| 파일 | viewport | 검증 의도 |
|---|---|---|
| `r11-lab-320.png` | 320 | 최소 viewport — PeakSeasonBanner 카운트다운/CTA 줄바꿈 회귀 (Codex #9) |
| `r11-lab-375.png` | 375 | 표준 모바일 — banner 3 phase + queue progress + FSM/Toss 매핑 표 |
| `r11-lab-640.png` | 640 | 태블릿 폭 — 2열 그리드 정렬 (cancel policy / 알림 신청 수) |

## 검증 포인트

1. **PeakSeasonBanner phase 3종**: pre / imminent / open. imminent에서 카운트다운이 `--countdown-warning-{bg,text}` 강조색으로 전환.
2. **Queue progress bar**: `--queue-progress-from/to` 그라디언트, ARIA `role="progressbar"` + `aria-valuenow`.
3. **FSM 라벨 표**: 13개 내부 enum + 한국어 라벨 (Codex #3 — 사용자에겐 라벨만 노출 확인용).
4. **Toss 매핑 표**: 7 Toss status × 내부 FSM × 사용자 라벨 cross-table (Codex #4).
5. **자연수 톤**: subscriberCount = 1247/2845/1923 (Codex #1 — 숙박앱 톤 1k~3k).

## 이연 (R12 분할 권고)

- R11.4 `<ReservationCheckout>` (TTL 카운트다운, inventory hold 5분)
- R11.5 `<PaymentMock>` (단계별 spinner, Toss IN_PROGRESS → DONE 시퀀스)
- R11.6 `<ReservationConfirmation>`
- R11.7 `<ReservationTimeline>` (FSM 전체 시각화, 내부 enum 접기 영역에 노출)
- R11.8 `<RefundFlow>` (REFUND_PENDING + 영업일 안내)
- R11.9 `<AdminShell>` + 5 라우트 wireframe + `requiresAdmin` guard + Grafana fallback card
- HomeView/AccommodationDetailView에 PeakSeasonBanner 실제 mount
