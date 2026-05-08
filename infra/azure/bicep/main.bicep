// ADR-0013 Phase 2 INFRA-AZ-0 — Resource Group skeleton (subscription scope).
//
// 본 모듈은 RG 3종만 생성한다. Container Apps / MySQL / ACR / Key Vault / AKS 등
// 실제 리소스 박제는 후속 INFRA-AZ-1 (prod-lite), INFRA-AZ-2 (scale-lab) dispatch에서.
//
// 적용 (사용자 명시 승인 후):
//   az deployment sub what-if -l koreacentral -f main.bicep
//   az deployment sub create  -l koreacentral -f main.bicep
//
// lint:
//   az bicep build -f main.bicep --stdout > /dev/null

targetScope = 'subscription'

@description('Resource Group region. Free Trial 권장: koreacentral.')
param location string = 'koreacentral'

@description('프로젝트 식별자. 모든 RG와 리소스에 tag로 박제.')
param projectTag string = 'tourdoum'

@description('Phase 식별자 — ADR-0013 결정 (8) prod-lite.')
param phaseTag string = 'phase-2-prod-lite'

var rgs = [
  {
    name: 'rg-tourdoum-prod-app'
    env: 'prod-app'
    purpose: 'static-web-apps + container-apps + acr'
  }
  {
    name: 'rg-tourdoum-prod-data'
    env: 'prod-data'
    purpose: 'mysql-flexible + key-vault + storage (delete-protected)'
  }
  {
    name: 'rg-tourdoum-scale-lab'
    env: 'scale-lab'
    purpose: 'aks-free + spot-pool + k6 + observability (single-shot)'
  }
]

resource rgResources 'Microsoft.Resources/resourceGroups@2024-03-01' = [for rg in rgs: {
  name: rg.name
  location: location
  tags: {
    project: projectTag
    env: rg.env
    phase: phaseTag
    'kill-policy': rg.env == 'prod-data' ? 'manual-approval-required' : 'kill-switch-allowed'
    purpose: rg.purpose
  }
}]

output rgNames array = [for (rg, i) in rgs: rgResources[i].name]
