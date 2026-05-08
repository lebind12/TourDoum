// scale-lab-dev.bicepparam — Phase 3 사전 박제. 실 deployment X.
//
// 사용 (사용자 명시 승인 + Phase 3 진입 게이트 후):
//   az deployment group what-if \
//     -g rg-tourdoum-scale-lab \
//     -f infra/azure/bicep/modules/scale-lab/scale-lab-stack.bicep \
//     -p infra/azure/bicep/parameters/scale-lab-dev.bicepparam

using '../modules/scale-lab/scale-lab-stack.bicep'

param aksName = 'aks-tourdoum-scale-lab'
param kubernetesVersion = '1.30.6'
param dnsPrefix = 'tourdoum-sl'

// Phase 3 초기: bootstrap system pool 1 노드만. 추가 system pool 보류.
param bootstrapSystemPoolCount = 1
param createExtraSystemPool = false

// Spot pools — 평시 0 노드. 30만 RPS 실험 직전 maxCount 까지 자동 스케일.
// mixed family 는 entry 둘 이상으로 분산 (eviction 위험 분산):
//   { poolName: 'spotd2s', vmSize: 'Standard_D2s_v5', minCount: 0, maxCount: 4, availabilityZones: [] }
//   { poolName: 'spotd2a', vmSize: 'Standard_D2as_v5', minCount: 0, maxCount: 4, availabilityZones: [] }
param spotPools = [
  {
    poolName: 'spotd2s'
    vmSize: 'Standard_D2s_v5'
    minCount: 0
    maxCount: 8
    availabilityZones: []
  }
]

param tags = {
  project: 'tourdoum'
  env: 'scale-lab'
  phase: 'phase-3-scale-lab'
  'kill-policy': 'kill-switch-allowed'
}
