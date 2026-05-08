// scale-lab RG 스택 — AKS Free + system pool + Spot user pool(s) wiring.
// scope: resourceGroup('rg-tourdoum-scale-lab').
//
// 본 스택은 ADR-0013 Phase 3 진입 직전 사전 박제. 실 deployment X.
// k6-operator / Redis / MariaDB / RabbitMQ / kube-prom-stack 은 INFRA-AZ-3b Helm chart 박제.

targetScope = 'resourceGroup'

@description('AKS cluster name. 예: aks-tourdoum-scale-lab')
param aksName string = 'aks-tourdoum-scale-lab'

param location string = resourceGroup().location

@description('Kubernetes 버전.')
param kubernetesVersion string = '1.30.6'

@description('DNS prefix.')
param dnsPrefix string = 'tourdoum-sl'

@description('Bootstrap system pool count (cluster 생성 직후).')
@minValue(1)
@maxValue(5)
param bootstrapSystemPoolCount int = 1

@description('추가 system pool 박제 여부. Phase 3 초기 false (bootstrap 만으로 충분).')
param createExtraSystemPool bool = false

@description('Spot user pool spec 배열. 빈 배열이면 spot pool 미생성. mixed family 는 여러 entry 로 분산.')
param spotPools array = [
  {
    poolName: 'spot1'
    vmSize: 'Standard_D2s_v5'
    minCount: 0
    maxCount: 8
    availabilityZones: []
  }
]

@description('Tags.')
param tags object = {
  project: 'tourdoum'
  env: 'scale-lab'
  phase: 'phase-3-scale-lab'
  'kill-policy': 'kill-switch-allowed'
}

// ---- AKS shell ----

module aks './aks.bicep' = {
  name: 'aks'
  params: {
    name: aksName
    location: location
    tags: tags
    kubernetesVersion: kubernetesVersion
    dnsPrefix: dnsPrefix
    bootstrapSystemPoolCount: bootstrapSystemPoolCount
  }
}

// ---- Optional extra system pool ----

module sysPlus './aks-system-pool.bicep' = if (createExtraSystemPool) {
  name: 'aks-system-pool-plus'
  params: {
    aksName: aks.outputs.aksName
    poolName: 'sysplus'
    vmSize: 'Standard_D2s_v5'
    nodeCount: 1
    minCount: 1
    maxCount: 3
    enableAutoScaling: true
  }
}

// ---- Spot user pools ----

module spotPool './aks-spot-userpool.bicep' = [for (p, i) in spotPools: {
  name: 'aks-spot-${p.poolName}'
  params: {
    aksName: aks.outputs.aksName
    poolName: p.poolName
    vmSize: p.vmSize
    nodeCount: 0
    minCount: p.minCount
    maxCount: p.maxCount
    availabilityZones: p.availabilityZones
  }
}]

// ---- outputs ----

output aksId string = aks.outputs.aksId
output aksName string = aks.outputs.aksName
output aksFqdn string = aks.outputs.aksFqdn
output aksNodeResourceGroup string = aks.outputs.aksNodeResourceGroup
output aksPrincipalId string = aks.outputs.aksPrincipalId
output spotPoolNames array = [for (p, i) in spotPools: spotPool[i].outputs.poolName]
