# ADR-0013 Brainstorm Notes — 2026-05-07 #3 회차

> **상태**: brainstorm 정리만. ADR 박제 보류. 추후 회차에서 brainstorm 추가 후 박제 예정.

본 노트는 차회 architect가 그대로 회수해 brainstorm을 이어가기 위한 자료다. 사용자 결정/미결정 + Codex 정정 + 도구 매핑 + 비용 path 박제.

---

## 1. 사용자 결정 (확정)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 성공 기준 layer | **최종 예약 확정 (confirmed) — DB writer 또는 Redis token 영속 시점** |
| 2 | 두 시나리오 비교 의도 | (A) 처리량 제한 vs (B) autoscaling 멀티인스턴스. 둘 다 학습 |
| 3 | DB 다중화 학습 범위 | **전체 layer 0~6** 포함 (read replica / Redis token / sharding / Kafka WAL) |
| 4 | 외부 도구 활용 | AWS 사용 OK, 단 비용 최소화 |
| 5 | AWS Documentation MCP | 사용자 머신에 등록 안내 송부 (`uvx awslabs.aws-documentation-mcp-server@latest`). 등록 완료 알림 대기 |
| 6 | 30만 RPS 도달 욕구 | confirmed 30만/sec **실측치 도달 시도**. 비용 상한 ~$10 |

## 2. Codex Critical Review 핵심 (researcher #13 회수)

### 진단

- **Tomcat 한계 보정**: Spring Boot default `accept-count=100, threads.max=200, max-connections=8192` (10000 아님). Little's Law 단일 Pod 이론 ~20k RPS, 실측 ~3~5k RPS (DB/Redis 50ms 시).
- **Aurora Multi-Master**: **2023-02-28 폐기 (unavailable)**. 단일 writer가 30만/sec ACID 비현실.
- **HPA cold-start gap = 90~145s** (HPA 15s + metric lag + Karpenter 55s + image pull + JVM warm-up). 30만 step burst → ~2970만 request 손실/대기 모델.
- **SQS 비용 함정**: 30만/sec × $0.40/M = **$432/hr** (시나리오 A dominant). buffer지 amplifier 아님 — writer 5k/sec, 입력 300k/sec → 1초당 295k backlog.
- **k6 ConfigMap 1MiB 제한**: JWT pool은 PVC/custom image/S3 initContainer 필수.
- **WAF aggregation 함정**: 모바일 CGNAT false positive — IP 단독 X, custom key/cookie + header spoofing 신뢰 경계 필수.

### 목표 재정의

기존: "30만 RPS 성공 처리"
**재정의**: "DB writer 병목 앞에서 A=얼마나 잘 버리고 흡수, B=얼마나 빨리 확장하고 얼마를 잃는지를 정량화"

## 3. DB 다중화 — 7 layer 효과

| Layer | 기법 | confirmed/sec | 비용 | 학습 가치 |
|---|---|---|---|---|
| 0 | Aurora 1 writer baseline | ~3k~5k | $ | 한계 정량화 |
| 1 | Read replica | **0** (write 무관) | $$ | 함정 학습 |
| 2 | Vertical scale (16xlarge) | ~30k | $$$$ | 비용 폭증 |
| 3 | ~~Aurora Multi-Master~~ | ~ | — | 폐기 사실 |
| 4 | Sharding (`accommodation_id` hash N=10) | shard당 30k → 30만 | $$$ | cross-shard tx 불가 |
| 5 | **Redis Lua 좌석 token 선점** | **30만/sec 도달** | $$ | **핵심** — confirmed = Redis token, eventual DB |
| 6 | Kafka WAL + consumer | 즉시 응답 + 영속 비동기 | $$$ | 5번과 결합 가능 |

## 4. 로컬 ↔ AWS 도구 매핑 (비용 절감 path)

| AWS | 로컬 대체 | 학습 손실 |
|---|---|---|
| EKS | Kind / K3d 멀티노드 | 거의 X |
| HPA + Prometheus Adapter | kind + metrics-server | 동작 동일 |
| Karpenter | ❌ kubectl scale 시뮬 (cold-start 실측만 손실) | 부분 |
| ALB | nginx / HAProxy | 거의 X |
| AWS WAF rate-based | nginx limit_req + Bucket4j edge | console UX X |
| SQS | RabbitMQ 또는 LocalStack SQS | 동등 |
| Aurora (writer + readers) | MySQL Group Replication 또는 docker MariaDB shard | sharding OK, storage layer X |
| Aurora Limitless | ❌ 없음 (preview 이론만) | 학습 X |
| ElastiCache Redis cluster | redis docker cluster (3M+3R) | 동등 |
| CloudFront | Varnish / nginx cache | edge 분산 X |
| Spot EC2 | ❌ 비용 모델만 ADR | 가격 변동 X |
| k6-operator | docker-compose k6 | generator ~5~30k RPS 한계 |

## 5. 비용 시나리오 비교

| 모드 | AWS 비용 | 학습 회수율 | confirmed/sec |
|---|---|---|---|
| 100% 로컬 | $0 | ~85% (Karpenter 실측만 손실) | ~3k~10k (노트북 한계) |
| 로컬 + AWS 1회 final 30분 | ~$2~10 | ~95% | 30만/sec 30s burst sustain |
| 100% AWS | $200~1000+ | 100% | 30만 1h sustain 시도 |

### $10 path 1h breakdown (서울 same AZ private path)

- EKS control plane $0.10
- k6-operator generator c7g.4xlarge × 4 spot $0.64
- target Spring pods c7g.large × 8 spot $0.48
- Aurora writer db.r6g.large $0.30
- ElastiCache Redis cluster cache.r6g.large × 3 $0.60
- NAT + same AZ private $0.10
- **합 ~$2.2/hr → $10이면 4시간 = 4회 시도**

### 도달 가능성 ($10 이내)

| 목표 | 가능성 |
|---|---|
| Generator 30만 RPS 발생 | ✅ (k6-operator 4 worker) |
| App 접수 30s sustain | ✅ |
| Redis token confirmed 30만/sec 30s burst | ✅ |
| Aurora 1 writer + sharding confirmed 30만/sec 1h sustain | ❌ (시간/비용 부족) |

## 6. 7개 미해결 결정 (사용자 컨펌 필요, brainstorm 후속)

| # | 항목 | 사용자 답변 / architect default |
|---|---|---|
| 1 | 성공 기준 | ✅ confirmed (사용자 답) |
| 2 | pending 시간 + 결제 | ⏳ default = authorization 5분 mock, capture 미구현 |
| 3 | Aurora writer 분산 학습 범위 | ✅ 전체 layer 포함 (사용자 답) |
| 4 | 실험 예산 | ✅ ~$10 이내 (사용자 답) |
| 5 | Overbooking + contention | ⏳ default = Redis token 선점 |
| 6 | WAF aggregation key | ⏳ default = IP + cookie/custom header 조합 (CGNAT 시뮬) |
| 7 | SQS Standard vs FIFO-sharded | ⏳ default = FIFO-sharded N=10~20 per accommodation hash |

## 7. ADR-0013 v1 task tree 후보 (researcher #13 권고 + 본 회차 정리)

```
INFRA-0  ✓ Local docker-compose Grafana/Loki/Promtail/Prometheus (완료, develop)
INFRA-1  Kind 멀티노드 + nginx LB + Spring N=2/4/8 (kubectl scale 시뮬)
INFRA-2  k6 local docker (generator 한계 ~5k~30k RPS) + JWT pre-gen pool
INFRA-3  saturation curve 측정 (단일 Pod p95 SLA RPS)
INFRA-4  Bucket4j Redis backend + nginx limit_req (시나리오 A 일부)
INFRA-5  RabbitMQ 또는 LocalStack SQS FIFO N=10 sharded (시나리오 A 비동기)
INFRA-6  Redis cluster + Lua 좌석 token 선점 (Layer 5)
INFRA-7  MariaDB N=4~10 shard (accommodation_id hash, Layer 4)
INFRA-8  (선택) Kafka local cluster + WAL (Layer 6)
INFRA-9  (선택) AWS Terraform/eksctl 자동화 — 30분 burst 자동 부팅+teardown

QA-3     단일 Pod saturation curve + p95 SLA 기반 RPS 산정
QA-4     시나리오 B 측정 — kubectl scale 시뮬로 cold-start gap 박제
QA-5     시나리오 A 측정 — 429/accepted/pending/confirmed/rejected/SQS depth/drain/DLQ/duplicate 분리
QA-6     A+B 병합 (실서비스 모사)
QA-7     layer별 confirmed/sec + 비용 + 복잡도 비교

ARCHITECT-1  layer 효과 비율 + cloud 30만 RPS 도달 산식 + Karpenter cold-start 추정 박제
ARCHITECT-2  (선택) AWS 1회 30분 burst final validation ($1.5~2)
```

## 8. 추가 brainstorm 후보 토픽 (사용자 신호 시)

- **결제 정책 상세** — authorization vs capture, 자동 환불 / TTL UX
- **Overbooking 비즈니스 모델** — 항공권식 oversell vs 100% 보장
- **Inventory representation** — 객실/날짜/투숙 단위 vs 객실타입/날짜 단위 trade-off
- **AWS Terraform 자동화 setup script** — 30분 burst를 위한 1-click 부팅
- **Spot capacity 실패 fallback** — 다중 region + multi-AZ + 인스턴스 패밀리 mixed
- **Karpenter NodePool YAML** — 학습 단계 mock vs 실제 cluster
- **k6 시나리오 script 샘플** — JWT pre-gen + reservation hot path + ramping-arrival-rate
- **DB writer 분산 IaC** — MariaDB Galera vs Group Replication vs sharding proxy (ProxySQL/Vitess)

## 9. AWS Documentation MCP 등록 안내 (사용자 액션)

```bash
claude mcp add --scope user aws-docs uvx -- awslabs.aws-documentation-mcp-server@latest
```

등록 완료 후 다음 dispatch부터 `mcp__aws-docs__*` 도구 활용 가능. EKS/Karpenter/HPA/WAF/RDS 공식 문서 직접 회수.

## 10. 산출물

- researcher 보고서: `/tmp/researcher-300krps-report.md` (#9 generator)
- researcher 보고서: `/tmp/researcher-300krps-scenarios-report.md` (#13 시나리오 + Codex)
- Codex 응답 로그: `.codex-20260507T231252Z.log`, `.codex-20260507T233534Z.log`
- Codex stream: `/tmp/codex-review-stream.log`

다음 회차 architect는 본 노트 정독 후 사용자 brainstorm 추가 + ADR-0013 v1 박제 진행.
