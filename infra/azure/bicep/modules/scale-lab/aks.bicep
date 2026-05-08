// AKS Free tier — control plane $0, SLA 없음.
// rg-tourdoum-scale-lab 에 배포 (caller scope). 단기 30만 RPS 실험 + 실험 후 RG 전체 삭제.
//
// 의존성: 없음. system pool 은 별 모듈로 분리 (default agent pool 사용 X).
// kubenet 네트워킹 (Phase 1) — Phase 2에서 Azure CNI Overlay 검토.
//
// 본 모듈은 cluster shell 만 — system pool / spot pool 은 별 모듈에서 attach.

@description('AKS cluster 이름. 예: aks-tourdoum-scale-lab')
@minLength(3)
@maxLength(63)
param name string

param location string = resourceGroup().location

param tags object = {
  project: 'tourdoum'
  env: 'scale-lab'
  module: 'aks'
}

@description('Kubernetes 버전. AKS supported versions: az aks get-versions -l <region>.')
param kubernetesVersion string = '1.30.6'

@description('DNS prefix. cluster fqdn 의 prefix.')
param dnsPrefix string = 'tourdoum-sl'

@description('Default system pool 의 임시 sentinel (Bicep API 가 minimum 1 system pool 요구). 후속 aks-system-pool.bicep 가 actual system pool 추가/스케일.')
param bootstrapSystemPoolName string = 'system'

@description('Default system pool VM SKU (bootstrap). 본격 system pool 은 aks-system-pool.bicep 에서 설정.')
param bootstrapSystemPoolVmSize string = 'Standard_D2s_v5'

@description('Default system pool node count (bootstrap, 1 권장).')
@minValue(1)
@maxValue(5)
param bootstrapSystemPoolCount int = 1

resource aks 'Microsoft.ContainerService/managedClusters@2024-05-01' = {
  name: name
  location: location
  tags: tags
  identity: {
    type: 'SystemAssigned'
  }
  sku: {
    name: 'Base'
    tier: 'Free'
  }
  properties: {
    kubernetesVersion: kubernetesVersion
    dnsPrefix: dnsPrefix
    enableRBAC: true
    nodeResourceGroup: 'MC_${resourceGroup().name}_${name}'
    networkProfile: {
      networkPlugin: 'kubenet'
      loadBalancerSku: 'standard'
      outboundType: 'loadBalancer'
    }
    agentPoolProfiles: [
      {
        name: bootstrapSystemPoolName
        mode: 'System'
        osType: 'Linux'
        osSKU: 'Ubuntu'
        vmSize: bootstrapSystemPoolVmSize
        count: bootstrapSystemPoolCount
        enableAutoScaling: false
        type: 'VirtualMachineScaleSets'
        availabilityZones: []
        maxPods: 110
      }
    ]
    apiServerAccessProfile: {
      enablePrivateCluster: false
      authorizedIPRanges: []
    }
    autoUpgradeProfile: {
      upgradeChannel: 'patch'
    }
    addonProfiles: {
      omsagent: {
        enabled: false
      }
    }
  }
}

output aksId string = aks.id
output aksName string = aks.name
output aksFqdn string = aks.properties.fqdn
output aksNodeResourceGroup string = aks.properties.nodeResourceGroup
output aksPrincipalId string = aks.identity.principalId
