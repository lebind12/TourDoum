// Spring Boot backend Container App.
// SystemAssigned MI → ACR pull (caller 가 ACR 'AcrPull' RBAC 부여).
// minReplicas=0 (평시), 시연 직전 1 toggle.
//
// Secrets:
//   - db-connection-string: jdbc:mysql://<fqdn>:3306/<db>?useSSL=true...
//   - jwt-private-key: PEM string
//   - app-insights-cs: AI connection string
// 절대 Bicep param default 또는 commit 금지. Deployment 시 @secure() param 으로만 주입.

@description('Container App 이름. 예: ca-tourdoum-api')
param name string

param location string = resourceGroup().location

param tags object = {
  project: 'tourdoum'
  env: 'prod-app'
  module: 'container-app'
}

@description('ACA managed environment id (output of container-apps-env.bicep).')
param environmentId string

@description('컨테이너 이미지. <acrLoginServer>/<repo>:<tag> 또는 mcr.microsoft.com/... 등.')
param image string

@description('내부 포트. Spring Boot management/server.port 와 일치.')
param targetPort int = 30080

@description('External ingress (public). false 면 환경 내부 전용.')
param externalIngress bool = true

@description('CORS allowed origins. Vercel preview/prod 도메인 + 로컬 dev.')
param allowedOrigins array = []

@minValue(0)
@maxValue(10)
param minReplicas int = 0

@minValue(1)
@maxValue(30)
param maxReplicas int = 2

@description('vCPU 코어. ACA Consumption 0.25/0.5/0.75/1.0/1.25/...')
param cpu string = '0.5'

@description('메모리. cpu 와 비례. 0.5 vCPU → 1Gi.')
param memory string = '1Gi'

@description('DB connection string — JDBC URL. @secure().')
@secure()
param dbConnectionString string

@description('JWT private key (PEM). @secure().')
@secure()
param jwtPrivateKey string

@description('App Insights connection string. @secure() (instrumentation key 노출 방지 관습).')
@secure()
param appInsightsConnectionString string

@description('Spring profile.')
param springProfilesActive string = 'prod-lite'

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: name
  location: location
  tags: tags
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    environmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: externalIngress
        targetPort: targetPort
        transport: 'auto'
        allowInsecure: false
        traffic: [
          {
            latestRevision: true
            weight: 100
          }
        ]
        corsPolicy: {
          allowedOrigins: allowedOrigins
          allowedMethods: [ 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS' ]
          allowedHeaders: [ '*' ]
          allowCredentials: true
        }
      }
      secrets: [
        {
          name: 'db-connection-string'
          value: dbConnectionString
        }
        {
          name: 'jwt-private-key'
          value: jwtPrivateKey
        }
        {
          name: 'appinsights-cs'
          value: appInsightsConnectionString
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'api'
          image: image
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          env: [
            {
              name: 'SPRING_PROFILES_ACTIVE'
              value: springProfilesActive
            }
            {
              name: 'SPRING_DATASOURCE_URL'
              secretRef: 'db-connection-string'
            }
            {
              name: 'JWT_PRIVATE_KEY'
              secretRef: 'jwt-private-key'
            }
            {
              name: 'APPLICATIONINSIGHTS_CONNECTION_STRING'
              secretRef: 'appinsights-cs'
            }
          ]
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: targetPort
              }
              initialDelaySeconds: 30
              periodSeconds: 30
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health/readiness'
                port: targetPort
              }
              initialDelaySeconds: 10
              periodSeconds: 10
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
        rules: [
          {
            name: 'http-rule'
            http: {
              metadata: {
                concurrentRequests: '50'
              }
            }
          }
        ]
      }
    }
  }
}

output appId string = app.id
output appName string = app.name
output appFqdn string = app.properties.configuration.ingress.fqdn
output principalId string = app.identity.principalId
