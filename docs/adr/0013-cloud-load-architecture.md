# ADR 0013 — Cloud Load Architecture (Azure local-first + guarded burst, 30만 RPS 학습)

- 작성일: 2026-05-08
- 상태: Accepted (v1)
- 관련: brainstorm `docs/notes/2026-05-07-adr-0013-brainstorm.md`, Azure handoff `docs/notes/2026-05-08-azure-cloud-planning-handoff.md`, infra plan `.worktrees/.../infra-azure-iac-skeleton/plan.md`

## 컨텍스트

학습 목적: **30만 RPS 부하를 실제 재현 + 견뎌내는 아키텍처를 직접 운영**. 단 (1) 가용 AWS credit 0, (2) Azure 학습 겸함, (3) 단기 3일 미만 burst 실험으로 비용 ~$30~80 한도, (4) 본 프로젝트는 단일 모놀리스 (분산 tx 없음).

3회차 brainstorm 정리, researcher 6 주제 조사 + Codex 교차 검토를 거쳐 본 ADR로 결정 박제. 세부 IaC/측정 절차는 후속 task로 분리.

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
| `/admin/sagas` | 실패/보상 진행 중 outbox 목록 | outbox table |

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

### scale-lab (단기 burst)
- **AKS Free** (control plane 무료, SLA 없음, <10 노드 권장)
- **Spot node pool** (D2s_v5, max-price -1는 capacity eviction 못 막음 — eviction policy `Delete` 명시)
- **k6-operator** (JWT pool은 PVC/initContainer, ConfigMap 1MiB 제한)
- **Self-host**: Redis Cluster + MariaDB shards + RabbitMQ + Prometheus/Grafana
- 1h burst 목표 비용 < $1, 끝나면 `az group delete --name rg-tourdoum-scale-lab --yes`

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

## 후속 ADR / Task 분해

### 후속 ADR 후보
- ADR-0014 — `inventory` 샤딩 + Redis Lua 좌석 token 설계 (scale-lab Phase 1 진입 전)
- ADR-0015 — Azure prod-lite 운영 절차 + kill switch policy
- ADR-0016 — Outbox 구현 표준 + idempotency key 정책

### Task tree (3일 학습 기준)

```
Day 1
  INFRA-AZ-0  Azure 계정 / az login / Cost budget $50/$80 / Resource Group 3종 / Bicep skeleton 박제
  INFRA-AZ-1  prod-lite Bicep — SWA + ACA + MySQL B1ms + ACR + Log Analytics 30d
  INFRA-AZ-2  GitHub Actions OIDC + Federated Credential + ACR build/push + ACA revision deploy
  BE-13       Reservation FSM + transition_log + outbox 테이블 + ReservationService 골격
  UI-R11      QueueLobby + ReservationTimeline + Admin route skeleton (5종)

Day 2
  BE-14       PG mock + REFUND 잡스케줄러 (@Scheduled + Shedlock)
  BE-15       Conditional UPDATE 패턴 적용 + idempotency key + outbox publisher worker
  QA-K6-1     local k6 saturation curve (단일 Pod p95 SLA RPS 측정)
  QA-K6-2     ACA replica 2~4 scale-out + 부하 측정 + Application Insights 관측
  ADMIN-1     Admin route 5종 데이터 wire (queue depth, payments state count, inventory, sagas, load-test panel)

Day 3
  INFRA-AZ-3  scale-lab Bicep — AKS Free + Spot + k6-operator + self-host Redis/MariaDB/RabbitMQ
  INFRA-AZ-4  Kill switch script 4종 (DRY_RUN guard + subscription guard)
  QA-K6-3     30만 RPS burst 측정 (1h 이내, 비용 < $1)
  QA-K6-4     Layer별 confirmed/sec 비교 + 비용 정리
  ARCH-1      ADR-0013 v2 박제 (실측 결과 + Azure 가격 고정 + Karpenter cold-start gap 실측)
```

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
