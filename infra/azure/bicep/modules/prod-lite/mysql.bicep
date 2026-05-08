// Azure Database for MySQL Flexible Server — B1ms (Free 12개월).
// rg-tourdoum-prod-data 에 배포 (caller 가 scope 결정).
//
// Phase 1: public access enabled + IP allowlist. Phase 2: VNet + Private endpoint.
// 백업 7일 retention, geo-redundant 비활성 (비용 절감).
//
// admin password 는 Bicep param + ACA secrets 주입. 본 모듈 또는 어떤 파일에도 commit X.

@description('MySQL Flexible Server 이름. 전역 unique. 예: mysql-tourdoum-prod')
@minLength(3)
@maxLength(63)
param serverName string

param location string = resourceGroup().location

param tags object = {
  project: 'tourdoum'
  env: 'prod-data'
  module: 'mysql'
}

@description('Admin login 이름. azure_superuser 등 reserved name 금지.')
param adminLogin string = 'tourdoum_admin'

@description('Admin password — DEPLOYMENT 시에만 secure param 으로 주입. 절대 default/commit X.')
@secure()
param adminPassword string

@description('MySQL 버전. 8.0.32 권장 (Flexible Server 지원).')
@allowed([
  '5.7'
  '8.0.21'
  '8.0.32'
])
param mysqlVersion string = '8.0.32'

@description('SKU 이름. Free 12개월: Standard_B1ms.')
param skuName string = 'Standard_B1ms'

@description('SKU tier. B1ms 는 Burstable.')
@allowed([
  'Burstable'
  'GeneralPurpose'
  'MemoryOptimized'
])
param tier string = 'Burstable'

@description('Storage size (GiB). Free 32 GiB.')
@minValue(20)
@maxValue(16384)
param storageSizeGB int = 32

@description('백업 보존 (일).')
@minValue(1)
@maxValue(35)
param backupRetentionDays int = 7

@description('초기 IP allowlist. dev workstation IP. 운영 시 Vercel egress / ACA outbound 추가.')
param firewallRules array = []

resource mysql 'Microsoft.DBforMySQL/flexibleServers@2023-12-30' = {
  name: serverName
  location: location
  tags: tags
  sku: {
    name: skuName
    tier: tier
  }
  properties: {
    administratorLogin: adminLogin
    administratorLoginPassword: adminPassword
    version: mysqlVersion
    storage: {
      storageSizeGB: storageSizeGB
      autoGrow: 'Enabled'
      iops: 360
    }
    backup: {
      backupRetentionDays: backupRetentionDays
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource fw 'Microsoft.DBforMySQL/flexibleServers/firewallRules@2023-12-30' = [for rule in firewallRules: {
  parent: mysql
  name: rule.name
  properties: {
    startIpAddress: rule.startIp
    endIpAddress: rule.endIp
  }
}]

output mysqlId string = mysql.id
output mysqlName string = mysql.name
output mysqlFqdn string = mysql.properties.fullyQualifiedDomainName
