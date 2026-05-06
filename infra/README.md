# infra — TourDoum

## 로컬 개발 환경

```bash
# 워크스페이스의 .env가 docker-compose에 자동 주입되지는 않는다.
# compose 파일이 자체 기본값을 들고 있고, 실제 앱은 .env를 읽는다.
docker compose -f infra/docker/docker-compose.yml up -d
docker compose -f infra/docker/docker-compose.yml ps
```

| 서비스 | 호스트 포트 | 비고 |
|---|---|---|
| MySQL 8.4 | 3306 | user/password/db = `tourdoum/tourdoum/tourdoum`, root pw = `rootpw` |
| Redis 7.4 | 6379 | 비번 없음 (로컬 한정) |
| Jenkins LTS (jdk21) | 8081 | 초기 setup wizard 비활성. plugin/job은 수동 구성. |

> 로컬 외부 노출 금지. 실서비스 배포는 본 ADR 범위 밖.

## Jenkins 사용법

1. http://localhost:8081 접속 → 좌측 `New Item` → `Pipeline` 또는 `Multibranch Pipeline`
2. SCM: 본 저장소 (로컬은 `file:///...` 또는 GitHub remote 연결 후)
3. Script Path: `infra/jenkins/Jenkinsfile`
4. 첫 빌드 후 `Manage Jenkins → Plugins`에서 필요 플러그인 보강:
   - Pipeline, Git, Docker, JUnit, AnsiColor, Timestamper, Workflow Aggregator

## 보안 / 비밀값

- compose 파일의 비밀번호는 **로컬 개발용 기본값**. 외부 노출 환경에서는 반드시 교체.
- 앱이 사용하는 DB 비번/JWT 시크릿은 `.env`에서 주입 (참고: 워크스페이스 `.env.example`).
- Jenkins credentials는 Jenkins 자체 credential store에 보관. 파일/repo 커밋 금지.
