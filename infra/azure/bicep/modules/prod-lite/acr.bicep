// ACR Basic — Spring Boot 이미지 저장. ACA Container App에서 SystemAssigned MI로 pull.
// admin user 비활성화 (RBAC + MI만). $5/월.
//
// 의존성: 없음. 가장 먼저 배포.
// 후속: container-app.bicep 의 image 파라미터로 `<acrLoginServer>/<repo>:<tag>` 참조.

@description('ACR 이름. 영소문자/숫자, 5-50자, 전역 unique. 예: acrtourdoumprodapp')
@minLength(5)
@maxLength(50)
param name string

@description('Region. RG region 상속 권장.')
param location string = resourceGroup().location

@description('태그.')
param tags object = {
  project: 'tourdoum'
  env: 'prod-app'
  module: 'acr'
}

resource acr 'Microsoft.ContainerRegistry/registries@2023-11-01-preview' = {
  name: name
  location: location
  tags: tags
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
    anonymousPullEnabled: false
    zoneRedundancy: 'Disabled'
  }
}

output acrId string = acr.id
output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
