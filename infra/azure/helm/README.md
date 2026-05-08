# scale-lab Helm chart skeletons

ADR-0013 Phase 3/6 진입 직전 사전 박제 (INFRA-AZ-3b). **실 AKS 배포 X** — Phase 6 (30만 RPS 실험) 게이트 후.

## 차트

| 디렉터리 | upstream chart | 네임스페이스 | 노드 풀 |
|---|---|---|---|
| `k6-operator/` | `grafana/k6-operator` | `k6` | system (operator), spot (TestRun runner) |
| `redis-cluster/` | `bitnami/redis-cluster` | `data` | system (data loss 회피) |
| `mariadb-shards/` | `bitnami/mariadb` × 10 (`helmfile.yaml.gotmpl`) | `data-shard-{0..9}` | system |
| `rabbitmq/` | `bitnami/rabbitmq` | `messaging` | system |
| `prometheus-grafana/` | `prometheus-community/kube-prometheus-stack` | `monitoring` | system |

## 의존성 / 설치 순서

```
1. cluster ready (INFRA-AZ-3a Bicep deploy)
2. NGINX Ingress  (helm install ingress-nginx ingress-nginx/ingress-nginx)
3. cert-manager   (선택, internal TLS)
4. prometheus-grafana  (먼저 — 모든 후속 ServiceMonitor 가 의존)
5. redis-cluster
6. mariadb-shards (helmfile 또는 수동 10 release)
7. rabbitmq
8. Spring Boot   (kubectl apply -f deployments/, ACR pull via MI 또는 secret)
9. k6-operator
10. k6 TestRun CR (loadgen namespace)
```

## Phase 6 진입 처방 (ADR-0013 §15)

5 요소 전부 사전 적재 필수:

1. **JWT 9000만 token 사전 적재** — Redis Cluster `maxmemory-policy=noeviction` 강제 (Codex 5회차 hard requirement). 적재 스크립트는 `infra/azure/k8s/scale-lab/jwt-loader-job.yaml` 별 박제.
2. **SNAT NAT Gateway** — AKS outbound 30만 RPS → SNAT port 고갈 위험. NAT Gateway 1개 추가 (Bicep `aks.bicep` 갱신 또는 별 모듈, INFRA-AZ-3c).
3. **k6 NIC PPS** — generator pod 당 NIC 가 PPS cap. parallelism 분산 (TestRun `parallelism=4` 권장 시작).
4. **MariaDB connection pool** — Spring HikariCP `maximum-pool-size` × shard 수 가 MariaDB `max_connections=200` 미만이어야. shard 10 × HikariCP 8 = 80 < 200.
5. **Spot capacity eviction** — D2s_v5 + D2as_v5 mixed family 분산 (`scale-lab-stack.bicep` `spotPools` 배열).

## JWT pool 처방 (Codex 5회차)

ConfigMap 1MiB 한계 → JWT 9000만 token 적재 시 ConfigMap 사용 불가. 처방:

- **PVC + Init Container**: dedicated PVC 에 JWT 사전 생성 (Spring `JwtPoolGenerator` Job), Spring Pod 가 read-only mount.
- **또는 Redis HSET**: 9000만 token 을 Redis cluster slot 분산 적재 (key prefix `jwt:`, hash slot 균등). `maxmemory-policy=noeviction` 필수.
- 본 chart skeleton 은 후자 (Redis 적재) 권장. 적재 Job manifest 는 별 박제.

## 검증

```bash
# Helm chart values syntax (helm 설치 시)
for f in infra/azure/helm/*/values*.yaml infra/azure/helm/k6-operator/testrun-template.yaml; do
  helm lint --strict --debug $(dirname "$f") 2>&1 || true
done

# YAML syntax (helm 미설치 시)
ruby -ryaml -e 'ARGV.each{|f| YAML.load_file(f)}' infra/azure/helm/**/*.yaml

# kind 로컬 dry-run (선택, kind + helm 설치 시)
kind create cluster --name tourdoum-scale-lab-dryrun
helm install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace --dry-run
helm install kube-prom prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  -f infra/azure/helm/prometheus-grafana/values.yaml \
  --dry-run --debug
# (RWO storageClass 없으므로 PVC 부분만 hostPath override 필요)
kind delete cluster --name tourdoum-scale-lab-dryrun
```

## 시크릿 정책

- 본 디렉터리 어떤 values.yaml 에도 평문 password 박제 X.
- 모든 password / connection string 은 `existingSecret` 참조. Secret 자체는 별도 SOPS / sealed-secrets / Azure Key Vault CSI driver 로 주입.
- helmfile.yaml 의 `secrets:` 항목은 SOPS 암호화 가정.

## 운영 정책

- **system pool 우선** — Redis / MariaDB / RabbitMQ / Prometheus / Grafana 는 system pool nodeSelector. Spot 거부 (data loss).
- **k6 runner 만 Spot** — operator 자체는 system, runner pod 는 toleration + nodeSelector 로 spot 만.
- **재현 불가능 ENV 박제 X** — Grafana admin / RabbitMQ Erlang cookie / Redis password 등은 별 Secret. PR 머지 후에 사용자 액션으로 적재.

## 후속 박제 후보

- **INFRA-AZ-3c** — AKS NAT Gateway Bicep 모듈 (Phase 6 진입 처방 §2)
- **INFRA-AZ-3d** — `infra/azure/k8s/scale-lab/` Spring Deployment + HPA + JWT loader Job + RBAC manifests
- **INFRA-AZ-5** — kind + helmfile CI workflow (smoke test → teardown)
- **INFRA-AZ-4** — Phase 2 kill-switch automation (Logic App / Automation Runbook), 보류 가능

## 미해결

- helmfile.yaml 의 SOPS / git-crypt 도입 결정 (사용자 권한)
- ingress-nginx Helm values 별 박제 (본 task 분량 넘침)
- cert-manager + Let's Encrypt 도입 결정 (Phase 6 internal TLS)
- AKS Workload Identity (Pod → ACR / Key Vault) — Phase 2 결정
