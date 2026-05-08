// Log Analytics workspace — ACA Environment + Application Insights 의 로그/메트릭 백엔드.
// retention 30d, daily quota 1 GiB (Free tier 안전).
//
// Codex 4회차 hard stop 보강: dailyQuotaGb=1 로 일 단위 비용 cap.

@description('Workspace 이름. 예: log-tourdoum-prod-app')
param name string

param location string = resourceGroup().location

param tags object = {
  project: 'tourdoum'
  env: 'prod-app'
  module: 'log-analytics'
}

@description('일별 ingest 한도 (GiB). 초과 시 ingest 중단 → 비용 cap.')
param dailyQuotaGb int = 1

@description('보존 기간 (일).')
@minValue(30)
@maxValue(730)
param retentionInDays int = 30

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
    workspaceCapping: {
      dailyQuotaGb: dailyQuotaGb
    }
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
  }
}

output workspaceId string = workspace.id
output workspaceName string = workspace.name
output customerId string = workspace.properties.customerId
