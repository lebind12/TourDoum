# 실사용 시나리오 기반 k6 테스트 기획 — 5회차 dispatch

> 작성일: 2026-05-08 (4회차 EOS)
> 대상: qa teammate, 5회차 첫 task 후보 (Phase 2 Azure 실 배포 검증)
> 관련: ADR-0013 Phase 1 도달 PASS (`cc7486d`) + Phase 2 진입 정당화

## 0. 기획 의도

기존 `phase1-saturation.js` = 단일 endpoint (`POST /api/reservations`) 한계 측정. 정합 기준이지만 **실사용 패턴과는 거리가 있음**.

본 기획안 = **실 사용자 행동을 7~8 시나리오로 묶은 mixed workload**. ADR-0013 Phase 2~6에서 단일 endpoint baseline + 실사용 mixed 둘 다 측정해 학습 회수율 ↑.

핵심 원칙:
- 한국 OTA 실무 트래픽 분포 reference (야놀자/여기어때/티몬 가정 비율)
- 시간 분포 (평시 / 성수기 burst / 자정 오픈런)
- 사용자 lifecycle 모델 (browse-only 70% / 전환 20% / 재방문 10%)
- ADR-0013 결정 (15) Phase 6 처방 5요소 측정 (token uniqueness / 메모리 / Spot eviction / 진입 gate / DB drain) 모두 검증

---

## 1. 사용자 lifecycle 모델 (k6 VU 분포)

### 1.1 70% — Browse-only (조회 위주)

```
Home → Search "강원" → Accommodations list → Accommodation detail → Reviews list → Exit
```

전환 X. 가장 흔한 사용자. 부하의 대부분 = GET 요청.

### 1.2 20% — Reservation flow (예약 전환)

```
Browse → Lobby (30~120s 대기) → Admission → Checkout → PaymentMock → Confirmation → Plans 추가 → Exit
```

성수기 시나리오에서 60~70%로 비율 ↑. 본 흐름이 BE FSM + outbox + Redis token 핵심 path.

### 1.3 5% — Refund (환불)

```
Login → My reservations → Detail → Refund request → REFUND_PENDING → Exit
```

별 outbox `RefundScheduled` 이벤트 → BE-15 publisher drain 측정.

### 1.4 5% — Authenticated browse (재방문)

```
Login → My plans → Plan detail → Edit → Save → Exit
```

JWT refresh / cookie credential 흐름 검증. `ADR-0011` CSRF 매트릭스 정합.

---

## 2. 시간 분포 (3 모드)

### 2.1 Mode A — 평시 baseline

- 시간: 평일 오후 (14:00~18:00)
- VU: 50~100 동시
- ramping: 5분 내 50 → 100
- target endpoint: 7개 (위 lifecycle 비율로 골고루)
- 측정: SLA 200ms 안정성 / outbox drain rate / DB connection pool / Tomcat thread

### 2.2 Mode B — 성수기 hot path (예약 폭주)

- 시간: 여름 성수기 오픈런 (예: 7/15 20:00 객실 오픈)
- VU: **2,000 → 30만 점진 증가** (5분 ramp-up)
- 비율 변경: 예약 80% / browse 15% / 기타 5%
- target endpoint 집중: `/api/queue/admission` + `/api/payment/start` + `/api/reservations/confirm`
- 측정: **가상 대기열 admission rate** + Bucket4j 429 비율 + outbox PENDING 누적 + Redis Lua token confirmed/sec

### 2.3 Mode C — 자정 오픈 (1h sustain)

- 시간: 자정 0:00 오픈 + 1h sustain
- VU: 30만 sustained
- ADR-0013 Phase 6 도달 시나리오
- 측정: confirmed/sec sustain (Redis master 메모리) + Spot eviction 영향 + 9000만 token 동시 holding 검증

---

## 3. k6 시나리오 script 템플릿

### 3.1 mixed-realistic.js

```javascript
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

export const options = {
  scenarios: {
    browse: { /* 70%, ramping-arrival-rate */
      executor: 'ramping-arrival-rate',
      startRate: 35, timeUnit: '1s',
      stages: [
        { duration: '2m', target: 70 },
        { duration: '5m', target: 70 },
      ],
      preAllocatedVUs: 100, maxVUs: 500,
      exec: 'browse',
    },
    reservation: { /* 20% */
      executor: 'ramping-arrival-rate',
      startRate: 10, timeUnit: '1s',
      stages: [
        { duration: '2m', target: 20 },
        { duration: '5m', target: 20 },
      ],
      preAllocatedVUs: 50, maxVUs: 200,
      exec: 'reservation',
    },
    refund: { /* 5% */
      executor: 'constant-arrival-rate',
      rate: 5, timeUnit: '1s', duration: '5m',
      preAllocatedVUs: 10, maxVUs: 50,
      exec: 'refund',
    },
    authBrowse: { /* 5% */
      executor: 'constant-arrival-rate',
      rate: 5, timeUnit: '1s', duration: '5m',
      preAllocatedVUs: 10, maxVUs: 50,
      exec: 'authBrowse',
    },
  },
  thresholds: {
    'http_req_duration{group:browse}': ['p(95)<500'],
    'http_req_duration{group:reservation}': ['p(95)<2000'],
    'http_req_duration{group:refund}': ['p(95)<1000'],
    'http_req_failed': ['rate<0.05'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:30080';

// JWT pool 100~1000 (PVC initContainer로 주입, ADR-0013 Phase 6 token uniqueness)
const JWT_POOL = JSON.parse(open('/jwt-pool.json'));

export function browse() {
  group('browse', () => {
    http.get(`${BASE}/api/accommodations?region=강원`);
    sleep(2);
    http.get(`${BASE}/api/accommodations/${randAccomId()}`);
    sleep(3);
    http.get(`${BASE}/api/reviews?accommodationId=${randAccomId()}&page=0`);
    sleep(2);
  });
}

export function reservation() {
  group('reservation', () => {
    const jwt = JWT_POOL[__VU % JWT_POOL.length];  // user identity 재사용 OK
    const headers = { 'Authorization': `Bearer ${jwt}` };

    // 1. Lobby admission token 획득
    const admit = http.post(`${BASE}/api/queue/admission`,
      JSON.stringify({ accommodationId: randAccomId() }),
      { headers: { ...headers, 'Content-Type': 'application/json' } }
    );
    check(admit, { 'admission 200': (r) => r.status === 200 });

    // 2. (admitted=true 시) Checkout
    const admissionData = admit.json();
    if (admissionData.admitted) {
      sleep(0.3); // 사용자가 결제 진입까지 시간

      // 3. Payment start (idempotency unique per request)
      const idempotencyKey = randomUUID(); // 매 요청 unique
      const pay = http.post(`${BASE}/api/payment/start`,
        JSON.stringify({ reservationId: admissionData.reservationId }),
        { headers: { ...headers, 'Idempotency-Key': idempotencyKey, 'Content-Type': 'application/json' } }
      );
      check(pay, { 'payment 200': (r) => r.status === 200 });
    }
  });
}

export function refund() { /* ... */ }
export function authBrowse() { /* ... */ }

function randAccomId() { return Math.floor(Math.random() * 13155) + 1; }
function randomUUID() { return crypto.randomUUID(); }
```

### 3.2 peak-burst.js (Mode B)

ramping-arrival-rate `2k → 300k` 5분 + 30s sustain. 비율: 예약 80%.

### 3.3 midnight-1h-sustain.js (Mode C, Phase 6)

constant-arrival-rate 30만 RPS sustain 1h. AKS Phase 6 진입 직전.

---

## 4. JWT pool 박제 (Phase 6 진입 gate)

ADR-0013 §"Phase 6 진입 gate" Codex 5회차 정정:
- 같은 JWT 재사용 OK (user identity)
- admission token / reservation id / idempotency key는 매 요청 unique 필수

진행:
1. seed 100~1000 user 박제 (`infra/k6/scripts/jwt-pool-gen.sh`)
2. 각 user JWT 발급 + json 파일 박제 (`/tmp/jwt-pool.json`)
3. k6 script `open('/jwt-pool.json')` 으로 read

```bash
# infra/k6/scripts/jwt-pool-gen.sh
SIZE=${1:-1000}
BASE_URL=${BASE_URL:-http://localhost:30080}
OUT=${OUT:-/tmp/jwt-pool.json}

echo "[" > "$OUT"
for i in $(seq 1 "$SIZE"); do
  EMAIL="k6-pool-$i@example.com"
  PW="K6PoolPwd!$i"
  # signup
  curl -sX POST "$BASE_URL/api/members/signup" -d "{\"email\":\"$EMAIL\",...}" -H "Content-Type: application/json" >/dev/null
  # login → JWT 추출
  JWT=$(curl -sX POST "$BASE_URL/api/auth/login" -d "..." -H "Content-Type: application/json" | jq -r '.data.accessToken')
  if [ "$i" -lt "$SIZE" ]; then SEP=","; else SEP=""; fi
  echo "  \"$JWT\"$SEP" >> "$OUT"
done
echo "]" >> "$OUT"
```

---

## 5. 측정 지표 (mixed workload)

### 5.1 처리량
- 시나리오별 RPS achieved vs offered
- p50/p95/p99/max latency per group
- error rate per group

### 5.2 BE 도메인
- transition_log row 증가 rate
- outbox PENDING/CLAIMED/DONE/FAILED 분포
- outbox drain rate (events/sec)
- BE-15 publisher tick × batch_size = 이론치 vs 실측 활용율

### 5.3 인프라
- DB connection pool 사용율 (Hikari)
- Tomcat thread pool 사용율
- Redis ops/sec (Lua token, Phase 5)
- JVM GC pause / heap

### 5.4 사용자 facing
- Lobby 대기 시간 분포 (admission 발급까지)
- Checkout TTL 만료율 (admission 후 결제 진입 못 한 비율)
- Refund 처리 지연 (mock = 즉시, 실 = 영업일)

---

## 6. 측정 후 박제

각 Mode 결과 → `docs/notes/2026-05-XX-qa-realistic-mode-{a,b,c}.md`:
- 시나리오 비율 vs 실제 분포
- 단계별 latency trend (browse / reservation / refund)
- BE 한계 정량화 (p95 SLA RPS / outbox drain bottleneck / Redis token max)
- Phase별 ADR-0013 도달 신호 갱신

---

## 7. 5회차 dispatch 절차 (qa teammate task #1 후보)

```
1. 새 worktree: `.harness/scripts/wt-new.sh 20-spec-tourdoum qa k6-realistic-mixed`
2. infra/k6/scripts/{jwt-pool-gen,mixed-realistic,peak-burst,midnight-1h-sustain}.sh|js 박제
3. Mode A 부터 실행 (5분, ~ramping 50→100 VU mixed) → docs/notes/...
4. Mode B (성수기 burst 5분 + 30s sustain) → docs/notes/...
5. Mode C는 Phase 6 진입 시 별 task (1h sustain, AKS Spot 환경)
6. agent-finalize.sh PASS → 보고
```

## 8. 사전 의존성 (5회차 진입 전)

- BE 측: queue admission endpoint (`POST /api/queue/admission`) 신규 박제 — BE backlog (Phase 1 BE는 reserve()까지만, queue lobby endpoint는 별 task)
- BE 측: 모든 시나리오 endpoint contract 확정
- ui R12 fixture handler 박제 (선택, e2e 회귀 가드)
- jwt-pool seed runner 박제 (5회차 첫 dispatch에 포함 가능)

## 9. 실측 후 ADR-0013 갱신 후보

- Phase 1 baseline 단일 endpoint = 416 RPS @ p95 1.25s (qa #35 박제됨)
- **Phase 1 mixed workload baseline** = 본 task 결과 (예: ~300 RPS mixed @ p95 800ms)
- Phase 2 ACA scale-out × N replica @ mixed = 각 N별 측정값
- Phase 5 Redis Lua token mixed = 결제 hot path 30만/sec 정합

---

## 10. 박제 후 인계

본 노트 = 5회차 첫 dispatch 인계서. qa teammate에 dispatch 시 본문 인용 + 본 노트 path 명시. 5회차 architect는 SESSION_HANDOFF §0 절차 8 (QA-K6-2 진입) 직전 또는 Phase 2 ACA 배포 직후 본 task 진입 결정.
