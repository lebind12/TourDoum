# QA-K6-1 — Phase 1 saturation baseline (단일 Pod)

**측정일**: 2026-05-08 18:29~18:34 KST (run id `phase1-20260508T182932`)
**스펙**: ADR-0013 §결정 (15) Phase 1 도달 신호 — local-dev 단일 Pod p95 SLA RPS.

## 환경

- BE: Spring Boot 1 인스턴스 (localhost:30080, profile=dev, JVM 21).
- DB: MySQL 8.4 1 컨테이너 (`tourdoum-mysql`), `max_connections=151`,
  `innodb_buffer_pool_size=128MiB` (default).
- Redis 1 (6380), idle.
- 머신: 사용자 로컬 (M-series macOS, 사용자 일반 작업과 공유 — clean room 아님).
- BE 13/15 적용 상태:
  - V18 마이그레이션 적용 완료 (reservations / reservation_transition_log /
    outbox / outbox_dead_letter 4 테이블 생성).
  - **단, ReservationService 가 FSM/outbox 경로 미사용** — 본 측정 중
    `transition_log`/`outbox` 0건 추가 (아래 §관찰 참조). BE-13 service 구현은
    "골격" 단계라 reservations 만 INSERT.

## 시나리오

`infra/k6/scripts/phase1-saturation.js` (`ramping-arrival-rate`):

| stage | duration | target RPS |
|---|---|---|
| 1 | 30s | 100 |
| 2 | 30s | 300 |
| 3 | 30s | 600 |
| 4 | 30s | 1000 |
| 5 | 30s | 1500 |
| 6 | 30s | 2000 |
| 7 | 30s | 2500 |
| 8 | 30s | 3000 |
| 9 (hold) | 30s | 3000 |

- 각 VU iteration = `POST /api/reservations` (Bearer JWT + Idempotency-Key + X-XSRF-TOKEN).
- 사용자 풀 = 20명 (`setup()` 단계에서 signup + login → token 풀 회수).
- 매 iteration 별 unique 날짜 + UUID Idempotency-Key.
- preAllocatedVUs=50, maxVUs=500.
- 임계값: `http_req_duration p(95) < 200ms`, `http_req_failed < 1%`.

## 결과 (raw `summary.json` 발췌)

| 지표 | 값 |
|---|---|
| iterations 완료 | 98,327 (325.04/s) |
| http_reqs | 98,368 (325.17/s) |
| **dropped_iterations** | **277,423 (917.06/s)** ← 목표 RPS 미달 |
| http_req_duration avg | 965.79 ms |
| http_req_duration med | 823.77 ms |
| **http_req_duration p(95)** | **2,355.65 ms** (SLA 200ms × 11.8 초과) |
| http_req_duration p(99) | 4,428.11 ms |
| http_req_duration max | 8,790.67 ms |
| http_req_failed | 0.00% (0 / 98,368) |
| reservations DB delta | +98,327 (62 → 98,389) — k6 카운트와 정확히 일치 |
| outbox / transition_log delta | 0 / 0 — service 경로 미적용 (아래) |

### 원본 파일

- `infra/k6/results/phase1-20260508T182932-summary.json` (k6 summary)
- `infra/k6/results/phase1-20260508T182932-db-before.txt`
- `infra/k6/results/phase1-20260508T182932-db-final.txt`

## 관찰 — 단일 Pod 한계 + 병목

1. **p95 SLA RPS (도달 신호)**: 본 측정에서 **단일 Pod p95<200ms 를 만족하는 RPS 는
   사실상 도달 X**. 가장 낮은 stage(100 RPS)에서도 latency 가 빠르게 누적되는
   패턴 — 첫 30s 100 RPS 동안 평균 latency 가 SLA 200ms 안으로 떨어지는지 stage별
   p95 분리 측정이 필요(본 통합 summary 한계). 이후 회차에서 stage별 trend export
   (`--out json=trend.jsonl` + jq 분리) 권고.
2. **achieved throughput ≈ 325 RPS**, 목표 3000 RPS 의 약 10.8% — 단일 Pod confirm
   엔드포인트의 실제 한계 처리량.
3. **dropped_iterations 277k (offered / achieved 비 ≈ 9.2:1)** — k6 가 목표 RPS 를
   주려 했으나 BE 가 못 받음. preAllocatedVUs 부족이 아니라 (max 500 까지 자동 확장
   되었음) **BE side blocking** 으로 VU 가 응답 대기에 묶임.
4. **0% error rate** — DB connection pool 고갈 / Tomcat queue 가득 / 502 등
   기능적 실패 무. 큐가 길어지지만 응답은 정확. 안정성 OK.
5. **outbox/transition_log delta = 0** — V18 스키마는 적용됐으나
   `ReservationService.confirm()` 이 V17 식 단순 INSERT 만 수행. **BE-13 후속
   (`saveTransition()` + `enqueueOutbox()` 호출 wiring) 미머지 상태.** Phase 1
   "도달 신호" 측정에 outbox drain rate 가 들어가야 하는데 본 회차는 0 → 별 측정
   필요. → **be 후속 dispatch 권고**.
6. 병목 후보 우선순위 (별 측정 미수행, 추정):
   - (a) Argon2id BE-4 password hash — 본 흐름엔 없음 (login 은 setup 1회).
   - (b) DB writer + InnoDB 락 (reservations PK + UNIQUE INDEX 경합).
   - (c) Tomcat thread pool default 200 — VU 500 이 200 thread 에서 줄세우기.
   - (d) Hibernate flush + auto-commit 패턴.

## Phase 2 진입 권고

- 단일 Pod 로는 SLA 미달 명확. **Phase 2 (ACA replica scale-out)** 진입 정당화 충분.
- 단, Phase 2 진입 전 선결:
  1. **BE-13 후속**: `ReservationService.confirm()` FSM/outbox 경로 wiring → outbox
     drain rate 측정 가능 상태. 본 측정의 핵심 결손.
  2. **stage별 latency 분해**: k6 `--out json` 으로 stage별 p95 trend 캡처 →
     "100 RPS 에선 SLA OK 인가?" 정량화. 본 회차의 통합 summary 한계.
  3. **Tomcat thread pool / Hikari pool / JVM heap 튜닝 1회**: 단일 Pod 한계가
     본 default 설정 한계인지, 진짜 코드 병목인지 분리.
- Phase 2 측정 = ACA replica 2~4 scale-out 시 동일 시나리오 재실행 → 선형 증가
  여부 확인. Phase 1 baseline ≈ **325 RPS / 단일 Pod** 가 비교 기준.

## 다음 단계 (qa)

- BE-13 후속 머지 알림 후 본 worktree 또는 신규 worktree 에서 재실행 → outbox
  drain rate 포함 baseline 갱신.
- 또는 stage별 trend 측정만 보강 회차 (BE 변경 없이 k6 옵션만).
