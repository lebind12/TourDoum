# ADR 0013 — Cloud Load Architecture (Azure local-first + guarded burst, 30만 RPS 학습)

- 작성일: 2026-05-08
- 상태: Accepted (v1)
- 관련: brainstorm `docs/notes/2026-05-07-adr-0013-brainstorm.md`, Azure handoff `docs/notes/2026-05-08-azure-cloud-planning-handoff.md`, infra plan `.worktrees/.../infra-azure-iac-skeleton/plan.md`

## 컨텍스트

학습 목적: **30만 RPS 부하를 실제 재현 + 견뎌내는 아키텍처를 직접 운영, 30만 confirmed/sec 1h sustain까지 도달**. 단 (1) 가용 AWS credit 0, (2) Azure 학습 겸함, (3) 본 프로젝트는 단일 모놀리스 (분산 tx 없음), (4) **시간 제약 없음 — WIP로 단계적 도달**.

3회차 brainstorm 정리, researcher 6 주제 조사 + Codex 교차 검토를 거쳐 본 ADR로 결정 박제. 세부 IaC/측정 절차는 후속 task로 분리.

### 본 ADR의 운영 모델 = WIP

본 ADR은 "X일 안에 끝낸다" 식의 시간 박제를 두지 않는다. 학습 단계의 본질 = **각 Layer를 직접 안정화하면서 함정을 만나고 처방을 박제**하는 것. 시간을 일정에 묶으면 안정화 시간이 0이 되어 측정 단계 진입 자체가 불가능해지는 게 4회차 Codex 검증의 핵심 지적이었다. 따라서:

- 시간 박제 없음. 각 Phase는 "stable / measured / 다음 Phase 진입 가능" 신호가 떨어진 시점에 끝난다.
- 비용 박제만 유지: scale-lab burst 실험 1회당 ~$1.5~3 (1h sustain 기준), 월간 누적 cap은 결정 (14) kill-switch에서.
- 모든 Phase는 backlog에 박제하되 "언젠가 달성" 기조. 1h sustain은 Phase 4 도달 시점에 결정 (15) 측정 절차로 직접 시도.

## 결정 (1) — 환경 분리 3종

```
local-dev      Docker Compose / kind. 비용 0. 기능·통합 검증 + k6 local 5~30k RPS.
prod-lite      Azure Container Apps + MySQL Flexible Server. 장기 시연 운영.
scale-lab      AKS Free + Spot. 단기 burst (30만 RPS). 끝나면 Resource Group째 삭제.
```

managed comparison(Azure Managed Redis, Service Bus Premium, Front Door WAF, Monitor managed Prometheus)은 **Phase 1에서 금지**. 학습/비교가 분명할 때만 Phase 2.

## 결정 (2) — Hot path UX = 가상 대기열

성수기/오픈런 burst 시나리오. 사용자 경험 = **A. 가상 대기열 (NetFUNNEL 패턴)**.

- `<QueueLobby>` 풀스크린: 대기 위치 / 예상 시간 / 이탈 버튼
- 진입 → signed admission token 발급 → 결제 단계 진입
- **push 방식 = 클라이언트 polling 5~10s** (인프라 추가 0). SSE/WebSocket은 P95≤5s push 요구일 때만 Phase 2.
- 30만 동시 대기 ≠ 30만 RPS. 5~10s polling이면 **3~6만 RPS** 만 발생 (Codex 정정).

## 결정 (3) — 결제·예약 트랜잭션 모델

본 프로젝트는 **단일 모놀리스 + DB 1개**라 분산 tx 자체가 없다. 따라서 SAGA orchestration framework (Temporal/Camunda/Spring Statemachine) **전부 X**. 다음 단순 패턴으로 충분:

- **상태머신 + `@Transactional` 도메인 흐름 + outbox로 외부 경계 분리**
- 구현체 = `ReservationService` 한 클래스 (orchestrator 아닌 그냥 도메인 서비스)
- 트랜잭션 경계 밖: PG mock 호출 / 알림 발송 / REFUND 잡스케줄러 / replica 동시성 → outbox + conditional UPDATE로 처리

### State 정의 (Reservation FSM)

```
QUEUED → ADMITTED → INVENTORY_RESERVED → PAYMENT_PENDING
       ┌──────────────┬──────────────┐
       ▼              ▼              ▼
   AUTHORIZED    REJECTED       CANCELLED
       ▼
    CAPTURED
       ▼
    CONFIRMED  ←──────  REVERSED  (보상)
       ▼
  REFUND_PENDING → REFUNDED
```

각 transition = idempotent + replayable. compensation은 outbox 이벤트로 비동기 발행.

### FSM 박제 = (D) Hybrid 5요소 (researcher + Codex 권고)

1. `reservation.state` **column SOT** (단일 진실원)
2. `reservation_transition_log` **append-only audit** (전자상거래법 시행령 §6 — 5년 보존)
3. **transactional outbox** (in-process dispatch, 알림, REFUND 트리거)
4. **idempotency key** (멱등성 보장, replica/retry 안전)
5. **PG webhook 대사(reconciliation) job** (실서비스 시 도입, 학습 단계는 mock)

Spring Statemachine은 가독성 도구로만 도입 검토. 영속화는 항상 column.

### 분산 안전 코드 패턴

```sql
-- replica 동시성 안전: conditional UPDATE
UPDATE reservations SET state = ?, updated_at = NOW()
 WHERE id = ? AND state = ?;  -- expectedPrev. rowsUpdated == 0이면 다른 replica 처리 → no-op
```

```java
@Transactional
void transitionTo(ReservationId id, String newState) {
  int rows = reservationRepo.transition(id, newState, expectedPrev(newState));
  if (rows == 0) return;
  transitionLog.append(id, expectedPrev(newState), newState);
  switch (newState) {
    case "FAILED" -> outbox.save(new Event("InventoryRelease", id));
    case "CONFIRMED" -> outbox.save(new Event("Notify", id));
    case "REFUND_PENDING" -> outbox.save(new Event("RefundScheduled", id, now().plus(3, MINUTES)));
  }
}
```

## 결정 (4) — PG mock + REFUND 잡스케줄러

- PG mock = **"결제하기" 버튼 1개**. 카드입력 UI 생략. PG 응답만 simulate (success / authFail / timeout).
- REFUND = **DB job scheduler** (Spring `@Scheduled` + Shedlock 락). outbox `RefundScheduled` due 행 폴링 → 처리. "즉시 처리하되 timestamp만 N분/N영업일 후로 시뮬" — 학습 단계 default.

## 결정 (5) — Admin UI

JWT + `@PreAuthorize("hasRole('ADMIN')")` 기존 인증 그대로. 별도 admin 토큰 X.

| 라우트 | 내용 | 데이터 소스 |
|---|---|---|
| `/admin/load-test` | k6 결과 + Grafana panel 임베드 | Prometheus + Loki |
| `/admin/queue` | QUEUED depth / admit rate / drop rate | Redis token store |
| `/admin/payments` | state별 카운트 + latency 히스토그램 | reservations / transition_log |
| `/admin/inventory` | shard별 잠금 현황 | inventory shard query |
| `/admin/outbox` | 실패/보상/대기 outbox 이벤트 + claim 상태 + retry 횟수 | outbox table |

## 결정 (6) — 30만 RPS 처방 layer

**hot spot 재정의**: `reservation` row가 아니라 **`inventory(accommodation_id, date)` row** (Codex). 객실·일자 재고에 락 경합.

| Layer | 기법 | 도입 시점 | confirmed/sec 기여 |
|---|---|---|---|
| 0 | MySQL 1 writer baseline | local-dev | ~3k~5k |
| 1 | Read replica | (학습용 함정 데모만) | 0 (write 무관) |
| 2 | Vertical scale | scale-lab Phase 1 | ~30k |
| 3 | ~~Aurora Multi-Master~~ | 폐기 (2023-02) | — |
| 4 | **`inventory` 샤딩 (`accommodation_id` hash N)** | scale-lab Phase 1 | shard당 30k → 30만 |
| 5 | **Redis Lua 좌석 token 선점** | scale-lab Phase 2 | 30만 confirmed/sec |
| 6 | Outbox + 비동기 영속 (RabbitMQ/Kafka) | scale-lab Phase 2 (선택) | 응답 ↑, 영속 비동기 |

scale-lab은 Layer 0~4 우선, Layer 5~6은 시간 여유 시.

## 결정 (7) — 환경 분리 의미

- "분산 환경" = **(a) Horizontal scale** (Container Apps/AKS replica). microservice 분리 아님.
- 같은 jar가 N replica로 떠도 분산 tx 발생 X (DB 1개 + isolation level + conditional UPDATE).
- microservice 분리는 본 ADR 범위 외 (3일 학습 비현실, Phase 2+).

## 결정 (8) — Azure 환경 (skeleton)

상세는 다음 회의에서 확정. 본 ADR엔 큰 결정만:

### prod-lite (장기 시연)
- **Frontend**: **Vercel** (Vue dist, `*.vercel.app` 도메인). Azure SWA 폐기.
- **Backend**: Azure Container Apps (`minReplicas=0, maxReplicas=2`, 시연 직전 1, `*.azurecontainerapps.io`)
- **DB**: Azure Database for MySQL Flexible Server **B1ms** (Free 12개월 — 750h + 32GB storage + 32GB backup, retention 1일)
- **Container Registry**: Azure Container Registry (ACR Basic ~$5/월) + Managed Identity pull
- **Auth state / outbox**: Phase 1 = MySQL fallback (Redis managed 비용 회피)
- **Secrets**: Phase 1 = ACA secrets (`az containerapp secret set`), Phase 2 = Key Vault + Managed Identity (시간 여유 시)
- **Observability**: Application Insights + Log Analytics 30d (Free 5GB/월)
- **CI/CD**: GitHub Actions OIDC + Federated Credential. 트리거 = `workflow_dispatch` 수동만 (auto deploy 금지, cost guardrail).
- **Bicep param**: `dev` 환경만 (demo 별도 환경 만들지 않음).

### Cross-origin 정책 영향 (Vercel + ACA)

Vercel `*.vercel.app` + ACA `*.azurecontainerapps.io` = cross-origin. ADR-0011 §"Cookie 정책" 다음으로 갱신:
- `SameSite=Strict` → **`SameSite=None; Secure`** 강제 (Lax도 POST cookie X)
- **CORS 필수**: `Access-Control-Allow-Origin: https://<…>.vercel.app`, `Allow-Credentials: true`, allowlist methods/headers (X-XSRF-TOKEN 포함)
- preflight OPTIONS 1회 캐시
- 같은 root domain(예: `tourdoum.example.com`) 도입 시 same-site 복원 가능 — 학습 단계엔 비도입

### scale-lab (burst 실험실, WIP)
- **AKS Free** (control plane 무료, SLA 없음, <10 노드 권장)
- **Spot node pool** (D2s_v5, max-price -1는 capacity eviction 못 막음 — eviction policy `Delete` 명시 + multi-zone + 인스턴스 패밀리 mixed + critical path별 on-demand fallback Pool로 1h sustain 안정성 완화)
- **k6-operator** (JWT pool은 PVC/initContainer, ConfigMap 1MiB 제한)
- **Self-host**: Redis Cluster + MariaDB shards + RabbitMQ + Prometheus/Grafana
- burst 실험 1회당 비용: 30s burst ~$0.10~$0.30 / 1h sustain ~$1.5~$3
- 실험 끝나면 `az group delete --name rg-tourdoum-scale-lab --yes` (RG kill-switch)

### 공통
- **Region**: `koreacentral`. Retail Prices API로 4종 가격 (D2s_v5 spot/on-demand, B1ms, Container Apps GiB-s, Storage) 박제 후 시작.
- **Resource Group 분리**: `rg-tourdoum-prod-app` / `rg-tourdoum-prod-data` / `rg-tourdoum-scale-lab`. data RG는 백업·승인 후만 삭제.
- **Cost guardrail**: Cost Management budget = $50 alert + $80 hard stop. Phase 1 자동 차단 chain 없음, 알림만.
- **IaC = Bicep** (Azure 학습 + MS Learn/MCP 매핑 + RG kill switch fit). Terraform state backend 부담 회피.
- **Azure MCP Server**: `npx -y @azure/mcp@latest server start` 등록. Entra ID local credential chain. 등록 후 az command 보조에 사용.

## 결정 (9) — 법적 근거

- **TourDoum은 PG 아님** → 전자금융감독규정 직접 적용 X (Codex 정정).
- 직접 적용 = **전자상거래법 시행령 §6**: 계약·청약철회·결제·재화공급 기록 **5년 보존**.
- `reservation_transition_log` audit 테이블이 이 요구를 충족.
- 전자금융감독규정은 PG 연동/계약 시 참고 (현재 mock이므로 미적용).

## 결정 (10) — Choreography / Service Bus

본 ADR Phase 1에서 **금지**. 외부 경계는 outbox + in-process worker로 처리.
Phase 2 학습으로 미룸: outbox consumer를 별도 ACA revision으로 분리 → 배포 단위 분산 학습 → Service Bus 도입.

## 결정 (11) — Outbox Publisher 동시성 (Codex 4회차 보강)

다중 replica 환경에서 outbox 폴링이 동일 row를 중복 처리하지 않도록 다음 5요소 박제:

1. **`SELECT ... FOR UPDATE SKIP LOCKED`** + `LIMIT N` claim — replica 간 경합 회피, MySQL 8 지원.
2. **`outbox.claim_state` enum**: `PENDING / CLAIMED / DONE / FAILED`. claim 시 `claimed_by`(replica id) + `claimed_at`(TTL 30s) 기록.
3. **Retry/backoff**: `attempt_count` + exponential backoff. max 5회 실패 시 `FAILED` + Admin 알림.
4. **Idempotent consumer**: 모든 외부 호출(PG mock / Email / SSE) 멱등 — `idempotency_key = outbox.id` 또는 `aggregate_id + transition`.
5. **Poison event 격리**: `FAILED` row는 별도 `outbox_dead_letter` 테이블로 이관. Admin UI `/admin/outbox` 에서 수동 재시도 가능.

`@Scheduled(fixedDelay=500)` worker + Shedlock(global lease) 또는 `FOR UPDATE SKIP LOCKED` 둘 중 하나. 모놀리스 단계엔 후자가 단순.

## 결정 (12) — Cold-start ↔ admission token TTL

ACA `minReplicas=0`은 평시 비용 0이지만 burst 진입 시 **cold-start gap** (image pull + JVM warm-up, 추정 30~90s) 동안 admission token TTL이 만료될 수 있음. 처방:

- **시연/부하 직전 `minReplicas=1`** (Container Apps secret + revision 갱신만으로 toggle 가능, 추가 비용 active 단가)
- **Admission token TTL = 5분 + grace 30s** + 만료 시 자동 재발급 endpoint (`/api/queue/refresh-admission`)
- **Token replay 방지**: token id를 outbox 또는 Redis에 기록, 재발급 시 invalidate

scale-lab(AKS Spot)은 별도 — HPA cold-start gap 90~145s 그대로 측정.

## 결정 (13) — MySQL B1ms CPU credit 감시

Burstable B1ms는 baseline 20% CPU + credit accumulation 모델. burst가 길면 credit 소진 → baseline으로 강제 throttle → 응답 latency 폭증. 처방:

- **Application Insights metric**: `mysql.cpu_credits_remaining` 알림 임계값 = **20%** (소진 임박 시 알림).
- **Day 2 측정 시 credit 소진 시나리오 1회 의도적 재현** — credit 0 상태에서 30s sustain → 결과 박제 (학습).
- credit 소진 빈번 시 GP 등급(Burstable 아님) 이행 검토 — 단 비용 폭증.

## 결정 (14) — $80 hard stop은 alert만으론 불가 (Codex 보강)

Azure Cost Management budget은 **알림만**, **자동 차단 chain은 별도 구성** 필요. 평가/알림 지연 8~24h 가능. 진짜 hard stop = runbook/automation:

- **Phase 1 (Day 1)**: budget $50 alert + $80 alert (이메일/Action Group) + **수동 kill-switch script** (`infra/azure/scripts/kill-scale-lab.sh` / `kill-prod-app.sh`) 박제.
- **Phase 2 (시간 여유 시)**: Action Group → Logic App / Azure Automation Runbook → 자동 RG delete (scale-lab만, prod-data는 절대 X).
- **수기 점검 cadence**: Day 1~3 동안 매일 1회 `az consumption usage list` 또는 Cost Management 대시보드 확인.

## 결정 (15) — 부하 목표 (WIP 단계적 도달)

본 ADR의 최종 목표 = **30만 confirmed/sec 1h sustain**. 시간 박제 없음. 단계는 다음 순서:

### confirmed 정의 (hard requirement)

> **"confirmed = Redis master 메모리 반영 시점 (sync replica ack 또는 AOF fsync는 미보장, eventual)"**. DB writer 영속은 outbox 기반 비동기 (eventual). 본 정의 없이 "DB writer 영속" confirmed면 30만/sec 1h sustain은 단일 모놀리스에서 비현실이며, 본 ADR 결정 (5)·(6)의 Layer 5 Redis Lua token 도입 의의가 사라진다.

**confirmed durability 트레이드오프 (Codex 권고)**:
- 본 ADR Phase 6 default = master 메모리 반영. failover 시 in-flight token 손실 허용.
- replica ack 보장 또는 AOF fsync는 별도 옵션 (latency ↑ throughput ↓). Phase 6 결과에서 trade-off 측정 후 별도 ADR(0017 후보) 결정.
- 손실 허용 범위: failover ≤ 1회/시간, in-flight token ≤ 5초 분(=150만 token) 손실 SLO. 사용자 facing은 "예약 다시 시도해주세요" 안내로 처리.

### Phase별 측정 목표

| Phase | 측정 목표 | 도달 신호 |
|---|---|---|
| **Phase 1** | local-dev 단일 Pod baseline — p95 SLA RPS | 측정값 박제 |
| **Phase 2** | prod-lite ACA 2~4 replica scale-out — HPA cold-start gap (90~145s 추정) + ingress 429/queue depth/drop rate | 시연 가능 + 측정값 박제 |
| **Phase 3** | scale-lab Layer 0 (MySQL 1 writer) baseline — 3k~5k confirmed/sec 30s burst | 측정값 + 한계 박제 |
| **Phase 4** | Layer 4 (MariaDB shard N=10) — shard당 30k confirmed/sec 30s burst → 합 30만 30s burst | 측정값 박제 |
| **Phase 5** | Layer 5 (Redis Lua token) — 30만 confirmed/sec 30s burst (DB는 비동기 영속) | 측정값 박제 |
| **Phase 6 (최종)** | **30만 confirmed/sec 1h sustain** — Layer 5 stable + Spot eviction 처방 + memory/TTL 정책 검증 | 측정값 박제 + ADR-0013 v2 close |

각 Phase는 stable + measured 후 다음 진입. Phase 5 직후 Phase 6 진입 권장 (스택 신선도 유지).

### Phase 6 sustain 처방 (Codex 4·5회차 검증 반영, "보장" 아닌 "완화")

> 본 처방은 1h sustain의 **성공 가능성을 높이는 완화책**이지 보장이 아니다. 실패 시 함정 분석 → 처방 갱신 → 재시도 사이클 박제.

**Redis 메모리 산정 (Codex 5회차 정정)**:
- token TTL = **5분 base + ±30s jitter** (expiry storm 회피)
- 비동기 DB sync로 confirmed token 영속 후 Redis에서 회수
- 동시 holding token = 30만/sec × 300s = **9000만 token**
- 메모리 산식 = key+value+object overhead+expire dict+allocator fragmentation+replication buffer ≈ **key당 200~500B (실측 기준)**
- 9000만 token × 350B 평균 = **31.5GB master memory 추정** (key당 100B 과소 산정 정정)
- self-host Redis Cluster (3 master + 3 replica) on AKS, master 노드당 **메모리 32GB+** 인스턴스 (예: Standard_E4s_v5 32GB, koreacentral 가격 Retail API로 사전 고정)
- replica는 별도 메모리 — total 64GB+ provision
- **eviction policy = `noeviction`** (allkeys-lru 아님 — live confirmed token evict되면 정확성 깨짐). `noeviction` 시 메모리 부족하면 OOM error → Phase 6 진입 gate에 **9000만 key 사전 적재 부하 테스트** 박제.

**confirmed token uniqueness (Codex 5회차 정정)**:
- JWT pool 100~1000은 **auth 비용 줄이는 generator 편의**용. user identity는 재사용 OK.
- 단 **admission token / reservation id / idempotency key는 매 요청 unique** 필수 (replay detection / Redis token uniqueness / outbox dedupe 정확성).
- k6 script: `${JWT_FROM_POOL}` 재사용 + `${UUID()}` admission/reservation/idempotency 매번 신규.

**Spot eviction 완화 (보장 아님)**:
- multi-zone (3 AZ) + 인스턴스 패밀리 mixed (D2s_v5 + D2as_v5 + D4s_v5)
- **on-demand fallback Pool**: critical path 별도 박제 (k6-operator coordinator + Prometheus master + Redis cluster master 1대 최소). worker/replica는 spot OK.
- **PDB (PodDisruptionBudget)**: Redis master `maxUnavailable=0`, BE Spring `minAvailable=50%`, k6 worker `maxUnavailable=33%`
- **Anti-affinity**: Redis master 3 replica 같은 노드 회피. zone spread topology constraint.
- **Spare capacity**: target replica의 1.5× provision (HPA target=66%).
- **Eviction 측정 SLO**: 1h 중 eviction 발생 시 p99 latency / error rate / confirmed loss 박제. eviction 0회 보장은 안 함.

**Phase 6 진입 gate (추가, Codex 5회차)**:
- Redis cluster 9000만 token 사전 적재 부하 테스트 PASS
- AKS subscription quota 확인 (vCPU / public IP / load balancer)
- SNAT port 고갈 회피 (NAT Gateway 도입 또는 outbound rule 명시)
- Prometheus metric cardinality 한계 (per-pod / per-shard label 폭발 회피)
- k6 generator NIC PPS 한계 (c-series spot SR-IOV enabled)
- DB 비동기 영속 = **전량 (sample 아님)**. outbox publisher backlog drain SLO = 5분 이내.

**측정 후 박제**:
- Phase 6 결과 → ADR-0013 v2 (실측치 + Karpenter cold-start gap 실측 + 비용 정리 + 함정 처방 완성판)
- replica ack / AOF fsync durability 비교는 별도 ADR (0017 후보)

## 후속 ADR / Task 분해

### 후속 ADR 후보
- ADR-0014 — `inventory` 샤딩 + Redis Lua 좌석 token 설계 (scale-lab Phase 1 진입 전)
- ADR-0015 — Azure prod-lite 운영 절차 + kill switch policy
- ADR-0016 — Outbox 구현 표준 + idempotency key 정책

### Task tree (Phase 단위, 시간 박제 없음 — WIP)

각 Phase는 의존성과 stable/measured 신호로 진입/종료. 시간 일정은 사용자 운영 자유.

```
Phase 1 — local-dev baseline (선행 의존성 0)
  BE-13       Reservation FSM + transition_log + outbox 테이블 + ReservationService 골격
  BE-14       PG mock + REFUND 잡스케줄러 (@Scheduled + Shedlock)
  BE-15       Conditional UPDATE + idempotency key + outbox publisher worker (FOR UPDATE SKIP LOCKED 5요소)
  UI-R11      QueueLobby + ReservationTimeline + Admin route skeleton (5종)
  ADMIN-1     Admin route 5종 데이터 wire
  QA-K6-1     local k6 saturation curve (단일 Pod p95 SLA RPS 측정) ← 도달 신호

Phase 2 — prod-lite (Phase 1 stable 후)
  INFRA-AZ-0  Azure 계정 / az login / Cost budget $50/$80 / Resource Group 3종 / Bicep skeleton 박제
  INFRA-AZ-1  prod-lite Bicep — ACA + MySQL B1ms + ACR + Log Analytics 30d
  INFRA-VE-1  Vercel project import + GitHub 연동 + 환경변수 + preview URL allowlist
  INFRA-AZ-2  GitHub Actions OIDC + Federated Credential + ACR build/push + ACA revision deploy (workflow_dispatch)
  QA-K6-2     ACA replica 2~4 scale-out + 부하 측정 + Application Insights 관측 ← 도달 신호 (시연 가능)

Phase 3 — scale-lab Layer 0 baseline (Phase 2 stable 후)
  INFRA-AZ-3a scale-lab Bicep skeleton — AKS Free + Spot 단일 node pool 만 (HPA 없음, Spring 1 Pod)
  INFRA-AZ-4  Kill switch script 4종 (DRY_RUN guard + subscription guard)
  QA-K6-3a    Layer 0 (MySQL 1 writer) — 3k~5k confirmed/sec 30s burst 측정 ← 도달 신호

Phase 4 — Layer 4 sharding (Phase 3 stable 후)
  INFRA-AZ-5  MariaDB N=10 shard Helm chart + Spring shard router (accommodation_id hash)
  QA-K6-4     shard당 30k confirmed/sec 30s burst → 합 30만 30s burst 측정 ← 도달 신호

Phase 5 — Layer 5 Redis Lua token (Phase 4 stable 후)
  INFRA-AZ-6  Bitnami Redis Cluster Helm chart on AKS (3 master + 3 replica, Standard_E4s_v5 메모리 32GB+, eviction=noeviction). Azure Managed Redis는 Phase 2 비용/관리 비교용 옵션 (Phase 1 금지 결정 (1) 유지).
  BE-16       Redis Lua 좌석 token 선점 스크립트 + ReservationService 통합
  BE-17       비동기 DB sync worker (outbox + token 회수)
  QA-K6-5     30만 confirmed/sec 30s burst (Layer 5) 측정 ← 도달 신호

Phase 6 — 30만 confirmed/sec 1h sustain (최종 목표)
  INFRA-AZ-7  multi-zone + 인스턴스 패밀리 mixed Spot pool + on-demand fallback 1대
  INFRA-AZ-8  k6 JWT pool PVC/initContainer (1000 pre-gen)
  QA-K6-6     1h sustain 시도 (warm-up 0.5h + sustain 1h + cool-down 0.5h, 시도당 ~$2.5)
              실패 시 함정 분석 → 처방 박제 → 재시도. 5회 시도까지 budget 안에서 가능.
  ARCH-1      ADR-0013 v2 박제 (실측 결과 + Azure 가격 고정 + 함정 처방 완성판) ← ADR close
```

후속 ADR 0014/0015/0016은 Phase 4/5/6 진입 직전 별도 박제 가능.

## 측정 관점

- Generator 30만 RPS 발생 가능 여부
- Ingress 429/reject/accept 비율
- Spring pods p50/p95/p99, CPU, GC, connection pool
- Redis Lua token confirmed/sec
- MariaDB shard writer confirmed/sec
- Outbox queue depth, publisher latency, drain time
- Scale-out 지연과 손실 request 수 (HPA cold-start gap 90~145s 실측)

## 참고

- 3회차 brainstorm: `docs/notes/2026-05-07-adr-0013-brainstorm.md`
- Azure handoff: `docs/notes/2026-05-08-azure-cloud-planning-handoff.md`
- Azure infra README: `infra/azure/README.md`
- Researcher 보고: 4회차 dispatch (Azure 4 주제 + 큐 push + 금융 FSM)
- STCLab NetFUNNEL: docs.stclab.com/products/netfunnel
- Toss Payments status enum: docs.tosspayments.com/reference
- 우아한형제들 결제 다중화: techblog/15236, /6447
- Azure Container Apps billing: learn.microsoft.com/azure/container-apps/billing
- Azure MCP Server: learn.microsoft.com/azure/developer/azure-mcp-server/overview
- 전자상거래법 시행령 §6 (law.go.kr)
