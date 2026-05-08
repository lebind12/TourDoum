// prod-data RG 스택 — MySQL Flexible Server 만 (Phase 1).
// scope: resourceGroup('rg-tourdoum-prod-data').
//
// MySQL FQDN 을 main.bicep 으로 노출 → prod-app-stack 에서 JDBC URL 구성 시 사용.

targetScope = 'resourceGroup'

@description('MySQL Flexible Server 이름. 전역 unique.')
param mysqlServerName string

param location string = resourceGroup().location

@description('MySQL admin login.')
param mysqlAdminLogin string = 'tourdoum_admin'

@secure()
@description('MySQL admin password — Bicep parameter file 또는 CLI --parameters 로만 주입.')
param mysqlAdminPassword string

@description('Allowlist firewall rules. dev IP 등.')
param firewallRules array = []

module mysql './mysql.bicep' = {
  name: 'mysql'
  params: {
    serverName: mysqlServerName
    location: location
    adminLogin: mysqlAdminLogin
    adminPassword: mysqlAdminPassword
    firewallRules: firewallRules
  }
}

output mysqlFqdn string = mysql.outputs.mysqlFqdn
output mysqlName string = mysql.outputs.mysqlName
