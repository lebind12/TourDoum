// dev.bicepparam — placeholder. 실 deployment 전 사용자가 sentinel 값 갱신.
//
// 사용:
//   az deployment sub what-if -l koreacentral \
//     -f infra/azure/bicep/main.bicep \
//     -p infra/azure/bicep/parameters/dev.bicepparam
//
// 시크릿 (mysqlAdminPassword, jwtPrivateKey) 은 본 파일에 적지 않는다.
// CLI 추가 -p 로 주입:
//   -p mysqlAdminPassword=$AZURE_MYSQL_PW \
//   -p jwtPrivateKey="$(< ./jwt_private.pem)"

using '../main.bicep'

param location = 'koreacentral'
param projectTag = 'tourdoum'
param phaseTag = 'phase-2-prod-lite'

param rgProdAppName = 'rg-tourdoum-prod-app'
param rgProdDataName = 'rg-tourdoum-prod-data'
param rgScaleLabName = 'rg-tourdoum-scale-lab'

param acrName = 'acrtourdoumprodapp'
param logWorkspaceName = 'log-tourdoum-prod-app'
param appInsightsName = 'appi-tourdoum-prod-app'
param acaEnvName = 'cae-tourdoum-prod-app'
param acaAppName = 'ca-tourdoum-api'

// 첫 deploy 는 placeholder. 이후 GH Actions (INFRA-AZ-2) 가 ACR 이미지로 revision update.
param containerImage = 'mcr.microsoft.com/k8se/quickstart:latest'

param allowedOrigins = [
  'http://localhost:5173'
  // TODO: Vercel preview/prod 도메인 추가 — INFRA-VE-1 dispatch 후 갱신.
  // 'https://tourdoum.vercel.app'
]

param minReplicas = 0
param maxReplicas = 2

param mysqlServerName = 'mysql-tourdoum-prod'
param mysqlAdminLogin = 'tourdoum_admin'

// dev workstation IP allowlist — 실 deployment 전 사용자가 갱신.
// 'startIp'/'endIp' 은 사용자가 `curl ifconfig.me` 등으로 확인 후 sentinel 교체.
param mysqlFirewallRules = [
  {
    name: 'placeholder-deny-all'
    startIp: '0.0.0.0'
    endIp: '0.0.0.0'
  }
]

// @secure() 파라미터 — 본 파일에서 박제 X. CLI -p 또는 GH Actions secrets 로 주입.
param mysqlAdminPassword = readEnvironmentVariable('AZURE_MYSQL_PW', '')
param jwtPrivateKey = readEnvironmentVariable('AZURE_JWT_PRIVATE_KEY', '')
