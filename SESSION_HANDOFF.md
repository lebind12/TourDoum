# SESSION_HANDOFF — TourDoum (2026-05-08 EOS, 4회차)

다음 회차 architect 재개용 박제. 본 회차 = ADR-0013 Phase 1 BE+QA 종료 + Azure prod-lite/scale-lab 사전 박제 + 다음 회차 = **Azure 실 배포 (Phase 2 진입)**.

본 회차 develop = e2a5069 → cc7486d, **189 commits ahead of origin/develop** (사용자 push 권장).

---

## 0. 다음 회차 첫 임무 (Phase 2 Azure 실 배포)

사용자 명시 = "새 세션에서 실 배포 진행". 다음 architect 첫 단계:

### 사용자 액션 완료 (5회차 진입 직전, 2026-05-08)
- ✅ `git push origin develop` (4회차 193 commits 푸시 완료, develop = origin/develop 동기)
- ✅ Azure 계정 검증: `todo.silhum@gmail.com` (primary lebind11@gmail.com과 별도 계정), 기본 디렉터리 `TODOSILHUMG...`, 리소스 0개 clean state
- ✅ **Subscription ID:** `5a248f32-365e-4baa-9f26-e4038cfd7b98` (실험용/Trial)

### ⚠️ 크레딧 만료 제약 (반드시 준수)
- **$200 USD 크레딧 30일 만료** = ~**2026-06-07 만료** (오늘 2026-05-08 기준)
- 4회차 인계 가정 "12개월 Free tier"는 부분 무효 — 12개월은 일부 서비스(B1ms MySQL 등)만, $200 크레딧은 30일 한정
- **5회차 우선순위:** prod-lite + scale-lab 1회 RPS 검증까지 압축 진행 (크레딧 소진 빠르게)
- **6회차 이후:** Stop/Deallocate로 크레딧 보존, Free tier 한도 내만 상시 가동
- **2026-06-01 만료 임박 시:** Pay-As-You-Go 전환 여부 사용자 결정 필요

### Architect 첫 단계
1. **Azure 계정 검증** — `claude mcp list` (azure-mcp ✓ Connected) + `az account show` 확인. subscription id 위 박제값 사용.
2. **Cost Management budget 박제** — `infra/azure/cost/*.json` 사용해 `az consumption budget create` (alert 임계 $50/$100/$150 권고, 30일 만료 고려)
3. **Resource Group 3종 생성** — `az deployment sub create --template-file infra/azure/bicep/main.bicep --parameters infra/azure/bicep/parameters/dev.bicepparam` (subscription id `5a248f32-365e-4baa-9f26-e4038cfd7b98` + tenant id + region 주입)
4. **prod-lite 본격 deployment** — `infra/azure/bicep/modules/prod-lite/prod-app-stack.bicep` + `prod-data-stack.bicep` `what-if` 후 사용자 승인 후 deploy
5. **GitHub Actions OIDC Federated Credential** — `infra/azure/github-oidc-setup.md` 절차서 따라 사용자 dashboard 액션
6. **Vercel project import** — `infra/vercel/github-integration-setup.md` 절차서 따라 사용자 dashboard 액션
7. **첫 deployment 검증** — `azure-acr-build-push.yml` workflow_dispatch + `azure-aca-deploy.yml` workflow_dispatch
8. **QA-K6-2 ACA replica scale-out 측정** — Phase 2 도달 신호

---

## 1. 본 회차 develop 머지 누적 (대화 시작 e2a5069 → cc7486d)

### Group 1 — qa #31 22 FAIL 카테고리 정리 (4 commit)
- `dc0eb6c` qa task #5 SPA nav 리팩터 + navigateTo helper, 19→14 FAIL
- `ed0bd49` ui task #2 design system contract 복원 (welcome-msg/btn-logout/guest-bar/#brand)
- `cae9521` fe task #2 + #3: AccommodationDetailView 렌더 크래시 픽스 + plans 셀렉터 + auth strict mode + nav helper 정합 (router.push 단일 경로)
- `0e8238b` be task #5 reservations contract = BE-3 CSRF 회귀 (Bearer 면제 / cookie credential 강제)

### Group 2 — ADR-0013 Phase 1 BE 박제 (4 commit)
- `be133dd` BE-13 Reservation FSM 13-state + transition_log + outbox 테이블 + V18 마이그레이션
- `9f09da8` BE-15 Outbox Publisher Worker (FOR UPDATE SKIP LOCKED + claim_state + retry/backoff + dead_letter + stale recovery)
- `a451cec` BE-14 PG mock + Toss 매핑 + Idempotency-Key Redis cache + Refund handler
- `0b35c8d` BE-13.1 wiring 처방 (confirm() 경로도 transition_log + outbox INSERT — qa #6 baseline 회귀 해소)

### Group 3 — qa baseline 측정 (3 commit)
- `11cbde4` qa #34 QA-K6-1 baseline 단일 Pod ~325 RPS p95 2.4s + BE-13 service wiring 결손 발견
- `eb5d6a2` qa #34 phase1 rerun (BE-14 머지 후, BE-13.1 시점 race로 outdated 판정)
- `4da27b6` **qa #35 phase1 3차 (BE-13.1 wiring 후)** — outbox 70 ev/s drain + transition_log 1:1 + **416 RPS @ p95 1.25s**, ADR-0013 Phase 1 도달 PASS

### Group 4 — Azure prod-lite/scale-lab 사전 박제 (4 commit)
- `de67f78` INFRA-AZ-0 Cost budget JSON + RG skeleton + kill-switch script
- `79f1354` INFRA-AZ-2 GitHub Actions OIDC + ACR + ACA + cleanup workflows
- `7b71565` INFRA-VE-1 Vercel project + GitHub 연동 + CORS allowlist 정책
- `62bf79b` INFRA-AZ-3a scale-lab Bicep AKS Free + on-demand system + Spot user pool
- `b9e4a39` INFRA-AZ-3b scale-lab Helm charts (k6 / Redis Cluster / MariaDB shards / RabbitMQ / kube-prom-stack)

### Group 5 — UI R10/R11 + dark 회귀 (3 commit)
- `a29c05c` R10 Tabs/Badge/Popover/SearchWidget transition 토큰화 + me-mock 캡처
- `b8e9138` R11.1~3 디자인 토큰 9종 + PeakSeasonBanner + fixture contract + `/dev/r11-reservation-lab`
- `ed0bd49` R11.1 dark variant 회귀 검증 (변화 X)

### Group 6 — ADR + 운영 정합 (4 commit)
- `c462a53` **ADR-0013 v1 박제** (218줄, 10 결정 + Phase 1~6 task tree)
- `b81c6f3` ADR-0013 §8 Vercel 갱신
- `3c6c687` ADR-0011 + 0013 Codex 4회차 검증 보강 (8건)
- `1321725` ADR-0013 시간 박제 제거 + WIP 단계 도달 모델 + 30만 1h sustain Phase 6 최종 목표
- `40d5173` ADR-0013 Codex 5회차 재검토 (NO-GO → GO with caveats), 10건 정정
- `07b87fa` ADR-0011 §"CSRF 면제/강제 매트릭스" 박제
- `cc7486d` ADR-0013 §결정 (15) Phase 1 도달 PASS 박제

### Group 7 — qa #30 Scenario A+B + qa #31 (5 commit, 본 회차 초반)
- `92f1b1d` qa #30 Scenario A 활성화
- `ecf7511` qa #30 Scenario A+B + BE-4 password helper 갱신
- `2d3f20a` qa #31 e2e 인라인 password 6 spec 일괄 갱신 (BE-4 정책 통과)

### Group 8 — fe FE-1 / FE-1.1 + ui R10 (이전 인계분 머지)
- `f204125` fe #14 ADR-0012 FE-1 keyset paging
- `e24ea0d` fe FE-1.1 cold-start fallback (qa #30 회귀 가드)
- `dde6543` be BE-4 비밀번호 보안 강화 (Argon2id + lockout + reset + revocation hook)
- `1958689` be BE-1.1 chat PUBLIC auto-join on signup (dev profile)

---

## 2. ADR-0013 Phase별 진척

| Phase | 상태 | 측정/박제 |
|---|---|---|
| **Phase 1 local-dev baseline** | ✅ **도달 PASS (2026-05-08)** | 416 RPS @ p95 1.25s / outbox 70 ev/s / transition_log 1:1 / 0% error |
| **Phase 2 prod-lite (Azure)** | 🟡 **다음 회차 진입** | INFRA-AZ-0/1/2 + INFRA-VE-1 박제 완료, 실 배포 사용자 승인 후 |
| Phase 3 scale-lab Layer 0 | ⚪ 사전 박제 (INFRA-AZ-3a/b) | Phase 2 stable 후 진입 |
| Phase 4 Layer 4 sharding | ⚪ Helm chart 박제 | Phase 3 stable 후 |
| Phase 5 Layer 5 Redis Lua token | ⚪ Bitnami Redis Cluster Helm 박제 | Phase 4 stable 후 |
| Phase 6 30만 confirmed/sec 1h sustain | ⚪ 함정 5요소 처방 박제 | 최종 목표, ADR-0013 §결정 (15) |

---

## 3. 진행 중 / 인계 backlog

### 본 회차 미진행 (사용자 신호 시 진입 가능)

- **qa task #9** — stage별 latency trend (signup → idle → reserve → payment) + outbox batch-size 50/100/200 튜닝. 5~10분 분량. dispatch 송부됨, 진입 X (race or 본 회차 종료).
- **be backlog**:
  1. `Reservation.status` legacy drop migration (V19)
  2. Toss webhook 대사 job (실 PG 환경 시)
  3. `/api/payments/start` ↔ `/api/reservations` UX 통합 (현재 분리 호출)
  4. `ChatChannelRepositoryTest` pre-existing fix (별 task)
  5. **BE 분할 운영 모델 박제** (Codex GO with caveats, 7 정정 후 spawn 준비)
- **fe backlog**:
  1. plans-flow `/plans/plan-001` deep-link 시드 (3 e2e FAIL)
  2. reservation-flow auth/reload 카스케이드 처방
  3. R12 fixture wire 협업 (ui R12 진입 시)
- **ui backlog**:
  1. **R12** (Checkout / PaymentMock / Timeline / AdminShell+guard / Banner mount + e2e) — R11 fixture contract 재사용
  2. ui-catalog R10 정정 (R11 토큰 미리보기)
- **infra backlog**:
  1. INFRA-AZ-4 (kill-switch automation Phase 2 — Logic App / Azure Automation Runbook)
  2. INFRA-AZ-5 (scale-lab CI workflow — kind 부팅 → helm install → smoke → teardown)
  3. Federated Credential 검증 helper (`infra/azure/scripts/verify-oidc.sh`)

### 잔여 e2e FAIL (qa #34 시점 14건 → BE-13.1 머지로 reservations 4건 자동 통과 예상 → 약 10건)
- plans-flow deep-link 시드 (3건) — fe/be 공동
- reservation-flow auth/reload 카스케이드 (1건)
- 잔여 strict mode (2건)
- 플레이키 notifications D (1건) — worker race
- 기타 FE/BE selector drift (3건)

---

## 4. 운영 사고 / 패턴 (본 회차 학습)

### 1. infra teammate 응답 패턴 (대표적 패턴)
- INFRA-AZ-0/1/VE-1/AZ-2/AZ-3a/AZ-3b 6 task 모두 commit + agent-finalize 후 SendMessage 본문 송부 X. idle notification만 송출.
- 처방 1: `.harness/agent-prompts/infra.md` §"보고 정책" 박제 (commit `.harness 4d108ba`). researcher.md 패턴 차용. 다음 회차 spawn에 적용.
- 처방 2: architect monitor + ScheduleWakeup autonomous loop 운영 사례 박제 (cycle #1~#6). worktree mtime/git log 직접 점검 + 머지 자동화.
- cycle #2 직후 한 차례 본문 ack 송부 → 정책 인지 가능. 그 후 다시 idle만 송출.
- 다음 회차 spawn 시 명시 ping에 "본문 응답 한 문장이라도" 강조 필수.

### 2. agent message race (계속 발생)
- be #1/#8/#12/#15 (3회차 4회) → be #5/#13.1 (4회차 2회) — 머지 안내 + 새 task dispatch race.
- qa task #7/#8 race — BE-13.1 머지 직전 시점 측정 → "결손 잔존" outdated 판정. 다음 회차에 architect가 "직전 머지 안내" vs "새 task" 명시 인용 패턴 박제.

### 3. BE-13 wiring 누락 회귀 (qa baseline에서 발견)
- BE-13에서 신규 `reserve()`만 transition_log + outbox 발행. 기존 `confirm()` legacy V17 INSERT 그대로.
- BE-13.1 처방 = `confirm()`도 wiring 추가 + ContractIT 회귀 가드.
- 학습: 신규 메서드 박제 시 기존 메서드 전체 grep + 같은 wiring 적용 검증 필요.

### 4. autonomous loop 운영 (본 회차 신규)
- background bash polling (`/tmp/infra-idle-monitor.sh`) — Bash timeout 30초로 일회성. 다음 회차에 longer-running 또는 ScheduleWakeup만으로 운영 검토.
- ScheduleWakeup 270~300초 cycle (cache TTL 5분 안) — cycle #1~#6 운영. infra 6 task 누적 머지 자동화 성공.
- 한계: teammate 본문 응답 race 시 architect가 직접 worktree 점검 + 머지. 자율 dispatch는 사용자 신호 + 명확한 의존성 그래프 후만.

---

## 5. 환경 / 하네스 박제 사항

### `.harness 4d108ba` agent-prompts/infra.md §"보고 정책" 추가
- task wake 직후 / 차단 시 / 완료 시 SendMessage 본문 필수
- 형식: commit / 변경 / 검증 / 잔여 risk / 머지+다음 권고
- 본문 ≤300단어, idle만 송출 금지
- 다음 회차 spawn에 적용

### Azure 환경 (사용자 액션 완료)
- Free Trial $200 credit 확보
- Azure CLI + `az login` 완료
- **Azure MCP `✓ Connected`** (`npx -y @azure/mcp@latest server start`)
- aws-docs MCP `✗ Failed` (정리 후보 — `claude mcp remove aws-docs`)

### Phase 2 진입 직전 사용자 액션 backlog
- subscription id / tenant id / region 결정 (default `koreacentral`)
- Bicep parameters/dev.bicepparam placeholder → 실 값 박제
- Vercel project import (사용자 dashboard 액션)
- GitHub Actions OIDC Federated Credential (Entra App + RBAC 사용자 액션)

---

## 6. ADR 누적

| ADR | 상태 | 본 회차 변경 |
|---|---|---|
| 0011 JWT 인증 | Accepted | §"Cookie 정책 cross-origin 갱신" + §"CSRF 면제/강제 매트릭스" 박제 |
| 0012 chat keyset paging | Accepted | 변경 X (3회차 박제) |
| 0013 Cloud Load Architecture | **Accepted v1 + Phase 1 PASS** | v1 박제 (218줄) → Codex 4·5회차 보강 (NO-GO→GO) → WIP 단계 모델 → Phase 1 PASS 박제 |

---

## 7. Notion + cl-memory 박제 (본 회차 종료 시 session-log skill로 박제 예정)

부모 페이지 `358ccedc-7709-8154-86fc-e2a46d7d8eef` 13번 sub-page (3회차 EOS, 사용자 휴식 직전) 박제 완료. 본 4회차 EOS sub-page는 **14번** 신규 박제 예정 — session-log skill 호출.

cl-memory 양자화 후보 (회차 종료 session-log에서):
1. ADR-0013 Phase 1 도달 — 416 RPS @ p95 1.25s + outbox 70 ev/s baseline (technical)
2. BE FSM + outbox + transition_log 5요소 (FOR UPDATE SKIP LOCKED + claim_state + retry/backoff + dead_letter + stale recovery) (technical)
3. BE-13 wiring 회귀 패턴 (신규 메서드 박제 시 legacy 메서드 wiring 누락 함정) (operational)
4. CSRF 면제/강제 매트릭스 (Bearer 면제 / cookie credential 강제) (technical)
5. infra teammate 응답 무응답 처방 + agent-prompts 보고 정책 (operational)
6. autonomous loop (background monitor + ScheduleWakeup 270s cycle) (operational)
7. agent message race 4회차에도 재발 — 처방 누적 backlog (operational)
8. navigateTo helper router state corruption (window.history.pushState 함정) (technical)
9. e2e SPA nav 리팩터 패턴 (page.goto 카스케이드 일괄 해소) (operational)
10. Azure prod-lite/scale-lab 6 IaC task 박제 누적 — Bicep + Helm + workflow + Vercel (operational)

---

## 8. 미해결 backlog (인계)

- **사용자 push origin develop** — develop 193 ahead (qa #36 + ADR 갱신 추가), push 안 하면 stale 위험
- **🐞 BE-4 IP-lockout false-positive race (긴급, 5회차 BE 첫 task 후보)** — `RedisLoginLockoutService` ip-threshold=10/10min window가 k6 동시 login burst 시 정상 password에도 fail 카운터 누적 → IP 락아웃. 4회차 회차 중 4회 `redis-cli FLUSHDB` 필요. 처방: per-IP 카운터를 success 시 reset 또는 fail 분류 정밀화 (timeout vs invalid password 분리)
- **outbox batch-size 50 → 200 default 변경** — qa #36 측정: drain 70 → 147 ev/s (+110%) ingestion +3% p95 -5% error 0%. `application.yml` `tourdoum.outbox.batch-size` default 갱신 후보 (별 task)
- **Argon2id signup thread-bound** — qa #36 측정: signup p95 660ms 단일 / 33s 동시 20VU. 운영 영향 낮음 (가입 빈도 ↓), k6 setup 동시 burst 영향만. Phase 6 측정 시 thread pool 분리 또는 burst rate limit 권고
- **qa task #9** stage별 latency + batch-size 튜닝 (5~10분)
- **23 e2e 잔여 FAIL** 카테고리별 처리 (deep-link / auth-reload / strict mode / 플레이키)
- **BE 분할 운영 모델** 박제 (Codex GO with caveats 7 정정 + CODEOWNERS + MIGRATION_REGISTRY 박제 후 spawn)
- **agent-prompts/infra.md** 갱신 효과 검증 (다음 회차 spawn에 적용)
- **R12 ui task** (Checkout / PaymentMock / Timeline / AdminShell + guard / Banner mount)
- **autonomous loop monitor** background polling 안정화 (Bash timeout 처방 또는 systemd-launchd 검토)
- **ChatChannelRepositoryTest** pre-existing 실패 fix
- **`Reservation.status` legacy drop** migration V19

---

## 9. 다음 회차 시작 체크리스트

1. `cd 20-spec-tourdoum && git status && git log --oneline -10` (본 회차 30+ 머지 확인)
2. `git push origin develop` (사용자 권장, 189 ahead)
3. **본 SESSION_HANDOFF 정독**
4. `claude mcp list` → azure-mcp ✓ + (aws-docs 정리 후보)
5. teammate 7명 + codex pane 재spawn (be / fe / qa / ui / infra / researcher + codex)
   - **infra spawn 시 `agent-prompts/infra.md` 4d108ba 보고 정책 적용 확인**
6. **Phase 2 진입 절차** — 본 SESSION_HANDOFF §0 사용자 액션 8단계
7. 본 파일 archive (`docs/sessions/2026-05-08-4.md`로 이동) 또는 삭제

---

## 10. 본 회차 cl-memory 양자화 후보 (위 §7 그대로)

회차 종료 session-log에서 박제. 본 SESSION_HANDOFF는 다음 회차 architect 인계용으로 우선 박제 후 archive.
