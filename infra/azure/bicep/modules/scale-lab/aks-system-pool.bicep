// AKS system node pool — control plane addon + ingress + observability 운영 워크로드 호스팅.
// **on-demand** (Spot 아님). Codex 5회차 critical path 정정: system pool 까지 spot 화하면 control plane addon eviction 시 cluster 운영 불능.
//
// 본 모듈은 aks.bicep 의 bootstrap pool 을 갱신 또는 추가 system pool 박제용.
// 기본 패턴: bootstrap (1 node) 그대로 사용 + spot user pool 만 추가. 본 모듈은 추가 system pool 이 필요할 때 사용.

@description('aks.bicep 의 cluster name (parent).')
param aksName string

@description('System pool name. 영소문자/숫자 1-12자. bootstrap 과 다른 이름.')
@minLength(1)
@maxLength(12)
param poolName string = 'sysplus'

@description('VM SKU. system pool 은 D2s_v5 (2 vCPU/8 GiB) 또는 D2as_v5.')
param vmSize string = 'Standard_D2s_v5'

@minValue(1)
@maxValue(10)
param nodeCount int = 1

@minValue(1)
@maxValue(10)
param minCount int = 1

@minValue(1)
@maxValue(10)
param maxCount int = 3

@description('Autoscaler on/off. system pool 은 보통 on.')
param enableAutoScaling bool = true

resource aksRef 'Microsoft.ContainerService/managedClusters@2024-05-01' existing = {
  name: aksName
}

resource sysPool 'Microsoft.ContainerService/managedClusters/agentPools@2024-05-01' = {
  parent: aksRef
  name: poolName
  properties: {
    mode: 'System'
    osType: 'Linux'
    osSKU: 'Ubuntu'
    vmSize: vmSize
    count: nodeCount
    minCount: enableAutoScaling ? minCount : null
    maxCount: enableAutoScaling ? maxCount : null
    enableAutoScaling: enableAutoScaling
    type: 'VirtualMachineScaleSets'
    maxPods: 110
    nodeLabels: {
      'tourdoum.io/pool': 'system'
      'tourdoum.io/eviction': 'on-demand'
    }
  }
}

output poolId string = sysPool.id
output poolName string = sysPool.name
