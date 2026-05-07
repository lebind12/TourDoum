# infra/observability — 로컬 관측 스택

ADR-0013 박제 전 사전 인프라. 30만 RPS 부하 발생 + Spring Boot Actuator 메트릭/로그 실시간 관측용.

운영 EKS 전환 시 helm chart 후보 — `kube-prometheus-stack` (Prometheus + Grafana + Alertmanager) + `loki-stack` (Loki + Promtail). 별도 ADR/INFRA-2 task에서 박제.

---

## 가동

```bash
docker compose -f infra/observability/docker-compose.yml up -d
```

| 서비스 | 컨테이너 | 호스트 포트 | URL |
|---|---|---|---|
| Prometheus | tourdoum-prometheus | 9090 | http://localhost:9090 |
| Grafana | tourdoum-grafana | 3000 | http://localhost:3000 (admin/admin) |
| Loki | tourdoum-loki | 3100 | http://localhost:3100 |
| Promtail | tourdoum-promtail | (no host port) | — |

검증:
```bash
curl -fs localhost:9090/-/ready
curl -fs localhost:3000/api/health
curl -fs localhost:3100/ready
# backend가 띄워진 상태에서:
curl -fs localhost:30080/actuator/prometheus | head -20
# Prometheus targets — http://localhost:9090/targets 에서 tourdoum-be UP 확인.
```

---

## Spring Boot Actuator 노출 endpoint

`backend/src/main/resources/application.yml` 에 박제 (관측 대상 포트 = 30080 agent / 8080 user).

| Endpoint | 경로 | 용도 |
|---|---|---|
| health | `/actuator/health` | liveness/readiness |
| info | `/actuator/info` | build/git info |
| metrics | `/actuator/metrics` | individual metrics 조회 |
| prometheus | `/actuator/prometheus` | Prometheus scrape target |

`management.metrics.tags.application=tourdoum` — 모든 메트릭에 application 라벨 자동 부여.

`management.tracing.enabled=false` — 학습 단계, micrometer-tracing(Tempo/Zipkin)은 별도 ADR.

---

## EKS 전환 시 helm chart 후보

| 구성 | helm chart | namespace |
|---|---|---|
| Prometheus + Grafana + Alertmanager | `prometheus-community/kube-prometheus-stack` | `monitoring` |
| Loki + Promtail | `grafana/loki-stack` (또는 `loki-distributed`) | `monitoring` |
| k6-operator | `grafana/k6-operator` | `k6-operator-system` |

ADR-0013 본 박제 + INFRA-2 task에서 helm values + IRSA 정책 박제 예정.

---

## 학습 단계 한정 사항

- Loki = local filesystem storage. 운영 전환 시 S3/GCS + boltdb-shipper.
- Grafana admin 비밀번호 = `admin`/`admin`. 사용자 로컬 한정.
- 메모리 사용량 ~1GB+. 무거우면 `prometheus + grafana` 만 띄우고 Loki/Promtail 생략 가능.
