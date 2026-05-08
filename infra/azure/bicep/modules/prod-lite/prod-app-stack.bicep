// prod-app RG 스택 — ACR + Log Analytics + Application Insights + ACA Environment + ACA Container App + AcrPull RBAC.
// scope: resourceGroup('rg-tourdoum-prod-app').
//
// MySQL FQDN/계정 정보는 prod-data-stack output 으로부터 main.bicep 이 주입.

targetScope = 'resourceGroup'

@description('ACR 이름. 영소문자/숫자.')
param acrName string

@description('Log Analytics workspace 이름.')
param logWorkspaceName string

@description('Application Insights 이름.')
param appInsightsName string

@description('ACA managed environment 이름.')
param acaEnvName string

@description('ACA Container App 이름.')
param acaAppName string

param location string = resourceGroup().location

@description('컨테이너 이미지. 첫 deploy 는 mcr.microsoft.com/k8se/quickstart:latest 등 placeholder 권장.')
param image string = 'mcr.microsoft.com/k8se/quickstart:latest'

param targetPort int = 30080

param minReplicas int = 0
param maxReplicas int = 2

@description('CORS allowed origins.')
param allowedOrigins array = []

@description('MySQL FQDN (prod-data-stack output).')
param mysqlFqdn string

@description('MySQL DB 이름.')
param mysqlDbName string = 'tourdoum'

@description('MySQL admin login.')
param mysqlAdminLogin string

@secure()
@description('MySQL admin password (prod-data-stack 와 동일 값).')
param mysqlAdminPassword string

@secure()
param jwtPrivateKey string

// ---- modules ----

module acr './acr.bicep' = {
  name: 'acr'
  params: {
    name: acrName
    location: location
  }
}

module la './log-analytics.bicep' = {
  name: 'log-analytics'
  params: {
    name: logWorkspaceName
    location: location
  }
}

module ai './appinsights.bicep' = {
  name: 'appinsights'
  params: {
    name: appInsightsName
    location: location
    workspaceId: la.outputs.workspaceId
  }
}

// LA workspace shared key — module output 으로 secure 전달이 어려우므로
// 'existing' 으로 listKeys() 호출.
resource laRef 'Microsoft.OperationalInsights/workspaces@2023-09-01' existing = {
  name: logWorkspaceName
  dependsOn: [ la ]
}

module acaEnv './container-apps-env.bicep' = {
  name: 'aca-env'
  params: {
    name: acaEnvName
    location: location
    logAnalyticsCustomerId: la.outputs.customerId
    logAnalyticsSharedKey: laRef.listKeys().primarySharedKey
  }
}

// JDBC URL — 평이한 구성. 운영 단계에서 sslMode/timezone 등 보강.
var dbConnectionString = 'jdbc:mysql://${mysqlFqdn}:3306/${mysqlDbName}?useSSL=true&requireSSL=true&serverTimezone=Asia/Seoul&user=${mysqlAdminLogin}&password=${mysqlAdminPassword}'

module acaApp './container-app.bicep' = {
  name: 'aca-app'
  params: {
    name: acaAppName
    location: location
    environmentId: acaEnv.outputs.envId
    image: image
    targetPort: targetPort
    minReplicas: minReplicas
    maxReplicas: maxReplicas
    allowedOrigins: allowedOrigins
    dbConnectionString: dbConnectionString
    jwtPrivateKey: jwtPrivateKey
    appInsightsConnectionString: ai.outputs.connectionString
  }
}

// AcrPull RBAC — ACA SystemAssigned MI 가 ACR 에서 이미지 pull 허용.
resource acrRef 'Microsoft.ContainerRegistry/registries@2023-11-01-preview' existing = {
  name: acrName
  dependsOn: [ acr ]
}

// AcrPull built-in role id.
var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource acaAppAcrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: acrRef
  name: guid(acrRef.id, acaAppName, acrPullRoleId)
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: acaApp.outputs.principalId
    principalType: 'ServicePrincipal'
  }
}

// ---- outputs ----
output acrLoginServer string = acr.outputs.acrLoginServer
output appInsightsConnectionString string = ai.outputs.connectionString
output acaAppFqdn string = acaApp.outputs.appFqdn
output workspaceId string = la.outputs.workspaceId
