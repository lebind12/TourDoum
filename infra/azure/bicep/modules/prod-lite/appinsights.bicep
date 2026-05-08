// Application Insights (workspace-based) — Spring Boot OTel exporter target.
// Phase 1: 학습 목적 sampling 100%. 비용 폭증 시 SamplingPercentage 조정.
//
// 의존성: log-analytics.bicep (workspaceId 주입).

@description('App Insights 이름. 예: appi-tourdoum-prod-app')
param name string

param location string = resourceGroup().location

param tags object = {
  project: 'tourdoum'
  env: 'prod-app'
  module: 'appinsights'
}

@description('Log Analytics workspace resource id (workspace-based AI 필수).')
param workspaceId string

@description('샘플링 비율 0-100. Phase 1 학습은 100%.')
@minValue(0)
@maxValue(100)
param samplingPercentage int = 100

resource ai 'Microsoft.Insights/components@2020-02-02' = {
  name: name
  location: location
  tags: tags
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: workspaceId
    SamplingPercentage: samplingPercentage
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
    DisableIpMasking: false
  }
}

output appInsightsId string = ai.id
output instrumentationKey string = ai.properties.InstrumentationKey
output connectionString string = ai.properties.ConnectionString
