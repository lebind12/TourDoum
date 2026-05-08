# QA-K6-stage-trend + outbox batch-size 튜닝

**측정일**: 2026-05-08 19:08~19:25 KST
**스펙**: ADR-0013 Phase 1 보강 — stage별 latency 분리 + publisher batch-size 튜닝.

## Part 1 — Stage별 latency 분리 (`phase1-stage-trend.js`)

`group()` 단위로 signup / login / reserve 단계 분리. closed-model
(constant-vus, 동시 사용자 수 = VU 수). 각 사용자 1회 signup→1회 login→N회 reserve.

### 1-A. STAGE_VUS=2 (단일 사용자 baseline)

| stage | avg | med | p(95) | p(99) | max |
|---|---|---|---|---|---|
| signup | 661 ms | 652 ms | 732 ms | 795 ms | 830 ms |
| login | 2.42 ms | 2.10 ms | 4.49 ms | 7.34 ms | 9.85 ms |
| reserve | (실행 X — 아래 §login fail) | | | | |

**핵심**: signup (Argon2id m=64MiB, t=3, p=1) ≈ **660ms 단일 호출 latency** —
ADR-0011 BE-4 Argon2id 비용. login 은 2~5ms — 빠름.

### 1-B. STAGE_VUS=20 (동시성)

| stage | avg | med | p(95) | p(99) | max |
|---|---|---|---|---|---|
| signup | 31.87 s | 31.61 s | 33.21 s | 33.21 s | 33.22 s |
| login | 1.34 s | 5.33 ms | 3.54 s | 3.85 s | 3.89 s |
| reserve | 9.61 s | 1.00 s | 32 s | 32 s | 32.02 s |

**핵심**: 동시 20 사용자 → signup p95 33s. Argon2id 가 CPU thread bound 라
queue 대기 누적. reserve p95 도 32s 까지 치솟음(BE 전체 큐 적체).

### 🐞 발견 — BE-4 IP-based login 락아웃 트리거

본 회차 stage-trend 와 후속 batch=200 saturation 시 setup() 단계가 token 0건으로
abort 되는 race 관찰. 원인:

- BE-4 `RedisLoginLockoutService` (commit BE-4 시점)
  - account-threshold = 5 (5분), account-lock = 30 분
  - **ip-threshold = 10**, ip-window = 600s
- 직전 stage-trend 의 login 다수 실패가 Redis `auth:login:fail:ip:127.0.0.1` 카운터를
  10 까지 누적 → IP 락아웃 → 후속 모든 login 401.
- 정상 password 인데도 락아웃 카운터 증가 메커니즘은 미상(별 원인 분석 필요 — k6
  대량 동시 호출 시 BE 가 false-negative 로 fail 기록 의심).

**처방 (즉시)**: `docker exec tourdoum-redis redis-cli FLUSHDB`. 실제로 본 회차 batch=200
saturation 직전 FLUSHDB 후 정상 동작 확인.

## Part 2 — Outbox publisher batch-size 튜닝

`tourdoum.outbox.batch-size` 를 default 50 → 200 으로 변경 후 phase1-saturation 5분
재실행. publisher tick 500ms 고정.

### 결과 비교

| 지표 | batch=50 (rerun3 `a71a396`) | **batch=200** | Δ |
|---|---|---|---|
| achieved RPS (ingestion) | 416 | **429** | +3.1% |
| dropped iterations | 251,302 | 248,129 | -1.3% |
| p(95) | 1,250 ms | **1,190 ms** | -4.8% |
| p(99) | 1,560 ms | 1,540 ms | -1.3% |
| max | 2,650 ms | 3,740 ms | +41% (가벼운 꼬리 ↑) |
| http_req_failed | 0% | 0% | — |
| reservations Δ | +124,445 | +127,617 | +2.5% |
| transition_log Δ | +124,445 (1:1) | +127,617 (1:1) | ✅ |
| outbox Δ | +124,445 (Notify) | +127,617 (Notify) | ✅ |

### 🚰 Drain rate (5×20s 샘플, k6 종료 직후)

| 경과 | PENDING | CLAIMED | DONE | drain Δ (20s) | rate (events/s) |
|---|---|---|---|---|---|
| t=20  | 179,633 | 0   | 72,499 | — | (Δ during k6 run) |
| t=40  | 176,433 | 157 | 75,542 | +3,043 | **152** |
| t=60  | 173,433 | 0   | 78,699 | +3,157 | **158** |
| t=80  | 170,633 | 179 | 81,320 | +2,621 | **131** |
| t=100 | 167,833 | 15  | 84,284 | +2,964 | **148** |

**Steady drain ≈ 147 events/sec** (vs batch=50 의 70 events/sec). **+110%**.

이론적 최대 = 200 × 2 ticks/s = 400 ev/s, 실측 36.7% 활용 (batch=50 의 70% 활용
보다 낮은 활용율 — claim batch 의 commit overhead/lock contention 추정).

## 종합

- **ADR-0013 Phase 1 도달 신호 보강**: stage 분포 + batch tuning 박제 완료.
- **Argon2id signup latency 660ms (단일)** → 동시 20 사용자에선 30s+ queue. 운영
  시 가입은 최저 RPS 기능이라 실제 영향 미미하나 **k6 setup 단계 race 주의**.
- **publisher batch=200 권장**: drain rate +110%, ingestion p95 -5%, 사이드이펙트
  무 (max latency 약간 ↑ 외). 운영 default 변경 후보.
- **BE-4 login lockout false-positive 가능성**: 별 원인 분석 필요 (be teammate
  follow-up 후보). k6 driver 의 동시 login burst 가 정상 password 임에도 fail
  카운터 누적 → 본 worktree 에서 운영 중 4회 FLUSHDB 필요했음.

## 다음 단계

- Phase 2 (ACA scale-out) 진입 시 동일 batch-size 200 + 본 stage-trend 재측정.
- 별 dispatch 권고: BE-4 login lockout race 원인 분석 (정상 password 가 fail
  카운터 누적되는 메커니즘).
