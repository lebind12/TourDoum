# QA-K6-1 — Phase 1 saturation rerun (BE-14 PG mock + Toss + Idempotency cache 머지 후)

**측정일**: 2026-05-08 18:53~18:58 KST (run id `phase1-20260508T185243`)
**전제**: develop @ `a451cec` (BE-14 머지 포함). 본 worktree rebase 적용.
**스펙**: ADR-0013 §결정 (15) Phase 1 도달 신호 — outbox drain rate 측정 검증.

## 시나리오

직전 baseline (commit `7eb6ebb`) 와 동일 — `infra/k6/scripts/phase1-saturation.js`
unchanged 재실행. ramping-arrival-rate 100→3000 RPS 5분.

## 결과 비교

| 지표 | baseline (BE-13 service 미적용) | rerun (BE-14 머지 후) | Δ |
|---|---|---|---|
| iterations 완료 | 98,327 | 87,568 | -10.9% |
| achieved RPS | 325.04 | 291.22 | -10.4% |
| dropped iterations | 277,423 | 288,179 | +3.9% |
| http_req_duration p(95) | 2,355.65 ms | 2,610.00 ms | +10.8% |
| http_req_duration p(99) | 4,428.11 ms | 4,200.00 ms | -5.2% |
| http_req_duration max | 8,790 ms | 5,540 ms | -37% (꼬리 짧아짐) |
| http_req_failed | 0.00% | 0.00% | — |
| reservations DB delta | +98,327 | +87,568 | (k6 카운트 정확 일치) |
| **transition_log delta** | **0** | **0** | (변화 없음 — 아래 §) |
| **outbox delta** | **0** | **0** | (변화 없음 — 아래 §) |

원본:
- `infra/k6/results/phase1-20260508T185243-summary.json`
- `infra/k6/results/phase1-20260508T185243-db-before.txt`
- `infra/k6/results/phase1-20260508T185243-db-final.txt` (수동 캡처)

## 🐞 핵심 발견 — BE-13.1 wiring 여전히 미적용

`ReservationService` 코드를 직접 확인:

- `/api/reservations` POST → `ReservationController.confirm()` →
  `ReservationService.confirm(...)` (line 109).
- 이 legacy `confirm()` 은 **V17 식 단순 INSERT 만 수행** —
  `transitionLogRepository.save()` / `emitOutbox()` 호출 부재.
- 신규 FSM 경로(`reserve()` line 197 + `transitionTo()` line 254 + `emitOutbox()` line 289+)
  는 코드 상 존재하나 **컨트롤러에서 호출되지 않음** (별도 진입점이 아예 없음).
- ReservationService 헤더 주석에도 명시: "본 영역은 13-state Reservation FSM의 진입점.
  기존 quote/confirm/myList/cancel는 **BE-14에서 FSM으로 통합 예정** — 본 task는
  신규 메서드만 추가하고 legacy 흐름은 그대로 둔다." → BE-14 머지 commit 도
  legacy 흐름 통합을 안 했음.

따라서 outbox drain rate 측정은 본 회차에서도 **불가**. BE-13.1 (또는 BE-16)에서
controller → FSM 경로 wiring 머지 후 재실행 필요.

## 마이너 관찰

- 처리량 -10% 는 BE-14 변경(Idempotency Redis 캐시 lookup, Toss mapping 한 번 더 추가)
  의 오버헤드로 추정. 그러나 본 흐름엔 PG mock chain / outbox drain 오버헤드는 없음
  (FSM 미진입). 즉 BE-14 의 실제 비용은 더 측정 필요.
- max latency 8.79s → 5.54s 로 꼬리 분포 개선 — Idempotency 캐시 효과 가능성 (중복
  키 lookup 빨라짐). 본 시나리오는 unique key 라 DB lookup 1회씩 발생, 캐시 hit X.
  변화 원인 불명, 추가 측정 필요.

## 다음 단계

1. **BE-13.1 (또는 BE-16) 후속 dispatch 필수** — `ReservationController.confirm()` 의
   엔드포인트가 신규 `reserve()` + `transitionTo()` 체인을 호출하도록 wiring.
2. 위 머지 후 본 worktree 또는 신규에서 phase1-saturation 3차 재실행 → outbox
   drain rate, transition_log delta, FSM transition latency 분리 분석.
3. (선택) stage별 latency trend — 본 회차 미수행. `--out json=trend.jsonl` 로 stage
   별 p95 추출 권고. 단일 Pod 에서 SLA 200ms 안 처리 가능 RPS 정량화.

## ADR-0013 Phase 2 진입 평가

- 단일 Pod p95 SLA 미달 명확 (×11.8 ~ ×13.0). Scale-out 학습 데모 가치 충분.
- 그러나 outbox drain 측정 부재 → Phase 1 도달 신호 한 항목 미확정. 본 결손 해소
  후 Phase 2 진입 권고. (Phase 2 진입 차단 사유는 아니다 — baseline 핵심 RPS/SLA
  지표는 박제됨.)
