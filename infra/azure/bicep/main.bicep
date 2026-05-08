// ADR-0013 Phase 2 — TourDoum Azure prod-lite (subscription scope).
//
// INFRA-AZ-0: RG 3종 생성.
// INFRA-AZ-1: RG scope 모듈 wiring (prod-data MySQL → prod-app ACR/LA/AI/ACA).
//
// Frontend = Vercel (ADR-0013 §8). 본 파일은 SWA 박제 X.
// scale-lab AKS = INFRA-AZ-3 별 task.
//
// 적용 (사용자 명시 승인 후):
//   az deployment sub what-if -l koreacentral -f main.bicep -p parameters/dev.bicepparam
//   az deployment sub create  -l koreacentral -f main.bicep -p parameters/dev.bicepparam
//
// lint:
//   az bicep build -f main.bicep --stdout > /dev/null

targetScope = 'subscription'

// ---------------- params ----------------

@description('Resource Group / 리소스 region. Free Trial 권장: koreacentral.')
param location string = 'koreacentral'

@description('프로젝트 식별자. 모든 RG와 리소스에 tag로 박제.')
param projectTag string = 'tourdoum'

@description('Phase 식별자.')
param phaseTag string = 'phase-2-prod-lite'

@description('prod-app RG 이름.')
param rgProdAppName string = 'rg-tourdoum-prod-app'

@description('prod-data RG 이름.')
param rgProdDataName string = 'rg-tourdoum-prod-data'

@description('scale-lab RG 이름. 본 dispatch 에서 RG skeleton 만 유지.')
param rgScaleLabName string = 'rg-tourdoum-scale-lab'

@description('ACR 이름. 영소문자/숫자 5-50자, 전역 unique.')
param acrName string = 'acrtourdoumprodapp'

@description('Log Analytics workspace 이름.')
param logWorkspaceName string = 'log-tourdoum-prod-app'

@description('Application Insights 이름.')
param appInsightsName string = 'appi-tourdoum-prod-app'

@description('ACA managed environment 이름.')
param acaEnvName string = 'cae-tourdoum-prod-app'

@description('ACA Container App 이름 (Spring Boot API).')
param acaAppName string = 'ca-tourdoum-api'

@description('컨테이너 이미지. 첫 deploy 시 quickstart placeholder, 이후 GH Actions 가 ACR 이미지로 갱신.')
param containerImage string = 'mcr.microsoft.com/k8se/quickstart:latest'

@description('CORS allowed origins. Vercel 도메인 + 로컬.')
param allowedOrigins array = [
  'http://localhost:5173'
]

@minValue(0)
@maxValue(10)
param minReplicas int = 0

@minValue(1)
@maxValue(30)
param maxReplicas int = 2

@description('MySQL Flexible Server 이름. 전역 unique.')
param mysqlServerName string = 'mysql-tourdoum-prod'

@description('MySQL admin login.')
param mysqlAdminLogin string = 'tourdoum_admin'

@secure()
@description('MySQL admin password — bicepparam / CLI --parameters 로만 주입. commit 금지.')
param mysqlAdminPassword string

@secure()
@description('JWT private key (PEM). commit 금지.')
param jwtPrivateKey string

@description('MySQL allowlist firewall rules.')
param mysqlFirewallRules array = []

// ---------------- RGs ----------------

var rgs = [
  {
    name: rgProdAppName
    env: 'prod-app'
    purpose: 'container-apps + acr + log-analytics + appinsights'
  }
  {
    name: rgProdDataName
    env: 'prod-data'
    purpose: 'mysql-flexible (delete-protected)'
  }
  {
    name: rgScaleLabName
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

// ---------------- prod-data stack (MySQL) ----------------

module prodData './modules/prod-lite/prod-data-stack.bicep' = {
  name: 'prod-data-stack'
  scope: resourceGroup(rgProdDataName)
  dependsOn: [
    rgResources
  ]
  params: {
    mysqlServerName: mysqlServerName
    location: location
    mysqlAdminLogin: mysqlAdminLogin
    mysqlAdminPassword: mysqlAdminPassword
    firewallRules: mysqlFirewallRules
  }
}

// ---------------- prod-app stack (ACR + LA + AI + ACA) ----------------

module prodApp './modules/prod-lite/prod-app-stack.bicep' = {
  name: 'prod-app-stack'
  scope: resourceGroup(rgProdAppName)
  dependsOn: [
    rgResources
  ]
  params: {
    acrName: acrName
    logWorkspaceName: logWorkspaceName
    appInsightsName: appInsightsName
    acaEnvName: acaEnvName
    acaAppName: acaAppName
    location: location
    image: containerImage
    minReplicas: minReplicas
    maxReplicas: maxReplicas
    allowedOrigins: allowedOrigins
    mysqlFqdn: prodData.outputs.mysqlFqdn
    mysqlAdminLogin: mysqlAdminLogin
    mysqlAdminPassword: mysqlAdminPassword
    jwtPrivateKey: jwtPrivateKey
  }
}

// ---------------- outputs ----------------

output rgNames array = [for (rg, i) in rgs: rgResources[i].name]
output mysqlFqdn string = prodData.outputs.mysqlFqdn
output acrLoginServer string = prodApp.outputs.acrLoginServer
output acaAppFqdn string = prodApp.outputs.acaAppFqdn
