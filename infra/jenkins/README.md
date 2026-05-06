# TourDoum — 로컬 Jenkins 운영 가이드

ADR-0001 결정: H9(GitHub Actions)를 **Jenkins**로 우회. 이 문서는 로컬 Jenkins를 GitHub 리포지토리와
연결해 CI 파이프라인을 처음 가동하는 절차를 설명한다.

---

## 1. Jenkins 컨테이너 부팅

```bash
# 프로젝트 루트(develop 또는 infra worktree)에서 실행
docker compose -f infra/docker/docker-compose.yml up -d jenkins

# 상태 확인
docker compose -f infra/docker/docker-compose.yml ps jenkins

# 로그 확인 (초기화 중에는 로그를 보는 게 좋음)
docker compose -f infra/docker/docker-compose.yml logs -f jenkins
```

접속: <http://localhost:8081>

> 초기 비밀번호(setup wizard 활성 시): `docker exec tourdoum-jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
> 현재 설정(`JAVA_OPTS: "-Djenkins.install.runSetupWizard=false"`)은 wizard를 **비활성화**하므로
> 초기 비밀번호 없이 바로 Jenkins 대시보드가 열린다.

---

## 2. 필수 플러그인 설치

Jenkins UI → _Manage Jenkins_ → _Plugins_ → _Available plugins_ 에서 설치:

| 플러그인 | 필요 이유 |
|---|---|
| Git | SCM 연동 |
| Pipeline | Declarative Pipeline 실행 |
| Multibranch Pipeline | develop/feature 브랜치 자동 감지 |
| GitHub Branch Source | GitHub API 연동 + webhook |
| AnsiColor | 컬러 로그 |
| Timestamper | 타임스탬프 출력 |
| JUnit | 테스트 리포트 수집 |
| HTML Publisher | JaCoCo 리포트 게시 (선택) |
| Docker Pipeline | 빌드 내 `docker run` 지원 |

설치 후 Jenkins 재시작: _Manage Jenkins_ → _Reload Configuration from Disk_ 또는 컨테이너 재시작.

---

## 3. GitHub Credentials 등록

GitHub Personal Access Token (PAT) 준비:
- Scope: `repo`, `read:org`, `admin:repo_hook`

Jenkins UI → _Manage Jenkins_ → _Credentials_ → _(global)_ → _Add Credentials_:

```
Kind:     Username with password
Username: <GitHub 사용자명>
Password: <PAT>
ID:       github-pat          ← Jenkinsfile에서 credentialsId로 참조
```

---

## 4. Multibranch Pipeline 생성

1. _New Item_ → 이름: `tourdoum` → _Multibranch Pipeline_ → OK
2. _Branch Sources_ → _Add source_ → _GitHub_
   - Credentials: `github-pat`
   - Repository HTTPS URL: `https://github.com/lebind12/TourDoum.git`
3. _Build Configuration_ → _Script Path_: `infra/jenkins/Jenkinsfile`
4. _Scan Multibranch Pipeline Triggers_ → 체크: _Periodically if not otherwise run_ (1분 간격, webhook 대안)
5. Save → Jenkins가 자동으로 브랜치를 스캔하고 첫 빌드를 트리거

---

## 5. GitHub Webhook 설정 (권장)

> 로컬 Jenkins는 외부에서 접근 불가. 로컬 개발에선 **ngrok** 또는 **주기적 폴링**으로 대체한다.

```bash
# ngrok으로 로컬 Jenkins를 임시 노출 (옵션)
ngrok http 8081
# → GitHub 리포 Settings → Webhooks → Add webhook
#   Payload URL: https://<ngrok-url>/github-webhook/
#   Content type: application/json
#   Events: Push, Pull request
```

폴링만 사용할 경우: Multibranch Pipeline _Scan_ 주기를 1~5분으로 설정하면 된다.

---

## 6. 첫 빌드 확인

1. Jenkins 대시보드 → `tourdoum` → 브랜치 목록 확인
2. `develop` 또는 `infra/feat-pipeline-bootstrap` 클릭 → 빌드 번호 클릭
3. _Console Output_ → 단계별 진행 확인:
   - `Checkout` → `Lint` → `Test` → `Build` → `Security (H10)`

---

## 7. 학습 친화 모드 — 테스트 실패 정책

ADR-0001 학습 친화 모드에 따라:

- **Test 실패 = `UNSTABLE`** (빌드 차단 X)
  - `catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE')` 로 감싸져 있음
  - 빌드 아이콘: 파란색(PASS) → 노란색(UNSTABLE) → 빨간색(FAILURE)
  - 현재: 노란색(UNSTABLE)까지 허용
- **격상 시점**: 도메인 모듈 1차 CRUD 완료 후 `failure`로 변경

---

## 8. 서비스 종료

```bash
# Jenkins만 종료
docker compose -f infra/docker/docker-compose.yml stop jenkins

# 전체 종료 (볼륨 유지)
docker compose -f infra/docker/docker-compose.yml down

# 볼륨 포함 삭제 (주의 — 빌드 히스토리 사라짐)
# docker compose -f infra/docker/docker-compose.yml down -v
```

---

## 9. 포트 참고

| 서비스 | 호스트 포트 | 컨테이너 포트 |
|---|---|---|
| Jenkins Web UI | 8081 | 8080 |
| Jenkins Agent (JNLP) | 50000 | 50000 |
| MySQL | 3306 | 3306 |
| Redis | 6379 | 6379 |

> 기존 mysql/redis 컨테이너(다른 프로젝트용)가 3306/6379를 점유하면 포트 충돌 발생.
> docker-compose.yml의 `ports` 값을 조정하거나 기존 컨테이너를 중지한 뒤 TourDoum 스택을 올린다.
