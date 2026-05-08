// AKS Spot user node pool — k6 generator + Spring/Redis/MariaDB/RabbitMQ scale-out 노드.
//
// Codex 5회차 정정:
//   - spotMaxPrice = -1: **가격 eviction 만 방지**. capacity eviction (Azure 가 spot 회수)은 막을 수 없음.
//   - evictionPolicy = 'Delete': eviction 시 노드 삭제 (재할당 X). HPA + cluster autoscaler 가 다른 zone/SKU 로 재배치 시도.
//   - taint `kubernetes.azure.com/scalesetpriority=spot:NoSchedule` 자동 부여 — workload 가 toleration 명시 필요.
//
// 한 풀당 1 SKU. mixed family (D2s_v5 + D2as_v5) 는 별 풀로 박제 (eviction 분산).

@description('aks.bicep cluster name (parent).')
param aksName string

@description('User pool name. 영소문자/숫자 1-12자.')
@minLength(1)
@maxLength(12)
param poolName string = 'spot'

@description('VM SKU. Codex 권장: D2s_v5 (2 vCPU/8 GiB) — k6 / Spring 워크로드 균형. mixed 는 별 풀.')
param vmSize string = 'Standard_D2s_v5'

@description('Initial node count. 평시 0 권장.')
@minValue(0)
@maxValue(20)
param nodeCount int = 0

@minValue(0)
@maxValue(20)
param minCount int = 0

@description('Autoscaler 상한. 30만 RPS 실험은 4-8 사이 권장.')
@minValue(1)
@maxValue(50)
param maxCount int = 8

@description('Spot 가격 cap. -1 = pay up to on-demand price (가격 eviction 방지). capacity eviction 은 막을 수 없음.')
param spotMaxPrice int = -1

@description('Eviction 정책. Delete = 노드 삭제. Deallocate = stopped 상태로 보존 (Spot 미지원, Delete 만).')
@allowed([
  'Delete'
])
param evictionPolicy string = 'Delete'

@description('OS disk 크기 (GiB).')
@minValue(30)
@maxValue(2048)
param osDiskSizeGB int = 50

@description('Availability Zones. 빈 배열이면 zone 미지정. ko-central 일부 zone 만 spot 가용.')
param availabilityZones array = []

resource aksRef 'Microsoft.ContainerService/managedClusters@2024-05-01' existing = {
  name: aksName
}

resource spotPool 'Microsoft.ContainerService/managedClusters/agentPools@2024-05-01' = {
  parent: aksRef
  name: poolName
  properties: {
    mode: 'User'
    osType: 'Linux'
    osSKU: 'Ubuntu'
    vmSize: vmSize
    count: nodeCount
    minCount: minCount
    maxCount: maxCount
    enableAutoScaling: true
    type: 'VirtualMachineScaleSets'
    scaleSetPriority: 'Spot'
    scaleSetEvictionPolicy: evictionPolicy
    spotMaxPrice: spotMaxPrice
    osDiskSizeGB: osDiskSizeGB
    availabilityZones: availabilityZones
    maxPods: 110
    nodeTaints: [
      'kubernetes.azure.com/scalesetpriority=spot:NoSchedule'
    ]
    nodeLabels: {
      'tourdoum.io/pool': poolName
      'tourdoum.io/eviction': 'spot'
      'kubernetes.azure.com/scalesetpriority': 'spot'
    }
  }
}

output poolId string = spotPool.id
output poolName string = spotPool.name
