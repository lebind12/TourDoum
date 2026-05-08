# scale-lab Bicep modules

ADR-0013 Phase 3 사전 박제 (INFRA-AZ-3a). **실 AKS 리소스 생성 X** — Phase 3 진입 시 사용자 승인 게이트 후.

목적: 30만 RPS 스트레스 테스트 실험실. AKS Free + Spot. 단기 실행 후 RG 전체 삭제 (`infra/azure/scripts/kill-scale-lab.sh`).

## 모듈

| 파일 | 역할 | scope | eviction |
|---|---|---|---|
| `aks.bicep` | AKS Free cluster shell + bootstrap system pool 1노드 (kubenet) | RG | on-demand |
| `aks-system-pool.bicep` | 추가 system pool (옵션, control plane addon 분리 시) | cluster (parent) | on-demand |
| `aks-spot-userpool.bicep` | Spot user pool (k6/Spring/Redis/MariaDB/RabbitMQ) | cluster (parent) | Spot Delete |
| `scale-lab-stack.bicep` | RG-scope orchestrator: aks + 옵션 system + spot pool 배열 | RG | — |

## AKS Free vs Standard 비용 비교

| 항목 | Free | Standard |
|---|---|---|
| Control plane | $0 | $0.10/시간 (~$73/월) |
| SLA | 없음 (best effort) | 99.95% |
| Cluster 한도 | 1000 노드 | 5000 노드 |
| 권장 사용 | 학습 / 단기 실험 | 운영 |

본 프로젝트는 **단기 30만 RPS 실험만** → Free tier OK. 실험 종료 후 즉시 RG 삭제.

## Spot 정책 (Codex 5회차 정정 반영)

- **`spotMaxPrice = -1`**: pay up to on-demand price → **가격 eviction 만 방지**. **capacity eviction (Azure 가 spot capacity 회수) 은 막을 수 없음.**
- **`evictionPolicy = Delete`**: eviction 시 노드 삭제 (재할당 X). HPA + cluster autoscaler 가 다른 zone/SKU 로 재배치.
- **자동 taint**: `kubernetes.azure.com/scalesetpriority=spot:NoSchedule` 자동 부여. 워크로드는 명시 toleration 필요:

```yaml
tolerations:
  - key: kubernetes.azure.com/scalesetpriority
    operator: Equal
    value: spot
    effect: NoSchedule
nodeSelector:
  kubernetes.azure.com/scalesetpriority: spot
```

- **system pool 은 Spot 금지** — control plane addon (CoreDNS / metrics-server / cluster-autoscaler) eviction 시 cluster 운영 불능. Codex 5회차 critical path 정정.
- **Mixed family** — 한 풀당 1 SKU. eviction 위험 분산 위해 D2s_v5 + D2as_v5 별 풀 박제 (`spotPools` 배열에 2 entry).

## Phase 3 진입 시 검증

```bash
# 1) Bicep lint
for f in infra/azure/bicep/modules/scale-lab/*.bicep; do
  az bicep build -f "$f" --stdout > /dev/null
done

# 2) bicepparam compile
az bicep build-params \
  -f infra/azure/bicep/parameters/scale-lab-dev.bicepparam \
  --stdout > /dev/null

# 3) what-if (사용자 승인 + scale-lab RG 사전 생성 후)
az deployment group what-if \
  -g rg-tourdoum-scale-lab \
  -f infra/azure/bicep/modules/scale-lab/scale-lab-stack.bicep \
  -p infra/azure/bicep/parameters/scale-lab-dev.bicepparam

# 4) deploy (Phase 3 게이트)
az deployment group create \
  -g rg-tourdoum-scale-lab \
  -f infra/azure/bicep/modules/scale-lab/scale-lab-stack.bicep \
  -p infra/azure/bicep/parameters/scale-lab-dev.bicepparam
```

## 운영 정책

- **Spot pool 평시 0 노드**: minCount=0, 실험 시작 시 manifests + HPA 가 noderequest → cluster autoscaler 가 maxCount 까지 확장.
- **30만 RPS 실험 종료 직후**: `infra/azure/scripts/kill-scale-lab.sh` 또는 GHA `azure-cleanup.yml` target=scale-lab. RG 자체 삭제로 비용 0.
- **AKS upgrade**: `upgradeChannel: patch` — patch 자동, minor 수동. 실험 진행 중 upgrade 충돌 회피.
- **`MC_<rg>_<aks>` 노드 RG**: AKS 가 자동 생성, 본 IaC 가 직접 관리 X. RG 삭제 시 같이 삭제됨.

## 후속 (INFRA-AZ-3b)

- `infra/azure/k8s/scale-lab/` Helm values / manifests:
  - k6-operator (grafana/k6-operator)
  - NGINX Ingress (ingress-nginx Helm)
  - Spring Boot Deployment + HPA (image = ACR `prod-app` 동일 또는 별 ACR 분리 결정)
  - Redis Cluster (Bitnami Helm 또는 redis-operator)
  - MariaDB shards (Bitnami Helm Galera 또는 app-level sharding)
  - RabbitMQ cluster (Bitnami Helm)
  - kube-prometheus-stack (kube-prom-stack Helm)
- `kind` 로컬 dry-run 검증 (k6-operator + NGINX + Spring stub) 후 AKS burst.

## 보류

- Azure CNI Overlay (Phase 2 검토)
- Azure Monitor managed Prometheus (Phase 2)
- AKS authorized IP ranges (Phase 1 학습 단계, 공개 API server)
- Cluster autoscaler tuning (Phase 3 실험 결과 후)
