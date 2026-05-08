// Azure Container Apps managed environment.
// Log Analytics 연결, VNet integration X (Phase 1).
// Consumption only — minReplicas=0 cold start 허용.
//
// 의존성: log-analytics.bicep (customerId + sharedKey).

@description('ACA Environment 이름. 예: cae-tourdoum-prod-app')
param name string

param location string = resourceGroup().location

param tags object = {
  project: 'tourdoum'
  env: 'prod-app'
  module: 'container-apps-env'
}

@description('Log Analytics workspace customerId (= properties.customerId GUID).')
param logAnalyticsCustomerId string

@description('Log Analytics workspace shared key. listKeys() 결과 주입.')
@secure()
param logAnalyticsSharedKey string

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsCustomerId
        sharedKey: logAnalyticsSharedKey
      }
    }
    zoneRedundant: false
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
  }
}

output envId string = env.id
output envName string = env.name
output defaultDomain string = env.properties.defaultDomain
