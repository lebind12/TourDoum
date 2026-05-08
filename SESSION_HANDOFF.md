# SESSION_HANDOFF — TourDoum (2026-05-07 EOS, 3회차)

다음 회차 architect 재개용 박제. 본 파일은 일회용 — 다음 회차 architect가 정독 후 archive(`docs/sessions/2026-05-07-3.md`로 이동) 또는 삭제.

본 회차 develop = 141 commits ahead of origin/develop. 이전 회차 종료 시점(47a42d8)에서 27 commit 추가 머지.

---

## 1. 본 회차 develop 머지 (커밋 순)

### Group 1 — JWT 인증 라인 (ADR-0011)
- **c7b65bf** be #63 BE-2 머지 (refresh rotation + token family + Redis denylist + previous-kid 활성). `--skip-build` 승인 (BE-only, FE 무관).
- **c08e335** be #1 BE-3 머지 (httpOnly cookie + CSRF token + Set-Cookie clear + body fallback). IT Testcontainers MySQL+Redis full flow PASS.
- **f5807f8** fe #11 FE-2 머지 (X-XSRF-TOKEN auto-injection + cookie credentials 통일 + reservations.ts direct fetch + 161 vitest).
- **f8d90d5** ADR-0011 §"BE-4 비밀번호 보안 강화" 7-box 분해 박제 (researcher #6 + Codex 교차 검토). 법정 vs 프로젝트 정책 분리 명시. KISA/개보위 2025-9호/ISMS-P/NIST 800-63B Rev.4/OWASP 출처 박제.

### Group 2 — 채팅 keyset paging (ADR-0012)
- **ede9a30** be #8 BE-1 머지 (Cursor DTO base64url JSON + /messages/older + LIMIT clamp 1≤?≤50 + content @Size 4000 + ?sinceId= 한시 호환).
- **d3c780e** be #12 BE-2 머지 (V17 4단계: chat_messages keyset idx / chat_members 역방향 / chat_channels DM pair UNIQUE+CHECK + last_message_id/at + idx + ROW_NUMBER backfill / JOIN 패턴 + ChatDmCreator REQUIRES_NEW + last_message guarded UPDATE).
- **9babe24** be #15 BE-3 머지 (dev-only seed runner profile 'chat-seed' + JdbcTemplate batch + --scenario=public|dm|mixed + Zipf 1/rank + 멱등 sentinel 채널명).

### Group 3 — ADR-0013 사전 인프라 + brainstorm
- **e716ecd** infra #10 머지 (AWS env 템플릿 `infra/aws/.env.aws.example` + IAM 최소 권한 + Observability 4종 docker-compose: Prometheus/Grafana/Loki/Promtail + backend Actuator scrape).
- **3c39798** ADR-0013 brainstorm notes 박제 (`docs/notes/2026-05-07-adr-0013-brainstorm.md`). **박제 보류, 추후 brainstorm 후 ADR 박제**.

### qa
- **3ec0bcf** qa #28 머지 (Notifications e2e 4/4: 비로그인/트리거/단건/전체 읽음, BE direct API).
- **60b7fe0** qa #30 + #28 C/D UI 회귀 머지 (chat-polling spec 골격 두 시나리오 test.skip, #28 C/D는 UI click + waitForResponse PASS).

### UI continuous (3 라운드)
- **7f8ed16** R7 — motion 토큰 5개 (`--motion-fast/base/slow + ease-standard/emphasized`) + Button micro-interaction + `/dev/me-mock`.
- **(중간)** R8 — Sheet/AlertDialog/Toast hardcoded duration → motion 토큰 + Input/Select transition 토큰화 + 14 screenshot (320/375/640).
- **b96a7d1** R9 — me-mock race fix + AlertDialog panel scale-in + Toast emphasized → standard 회귀.

### 하네스 + 운영
- **`.harness c30a36a`** `_resolve_base_ref` P0 보강 — local develop ahead 시 우선 사용 (5회 반복 false-trigger 처방).
- **`.harness fc8a94b`** `agent-prompts/researcher.md` 신규 — 외부 자료 조사 + Codex 교차 + architect 보고 역할 박제. CLAUDE.md §3 역할 표 갱신.

---

## 2. ⚠️ 진행 중 task (다음 회차 첫 처리)

### **fe #14 ADR-0012 FE-1 (ChatView keyset state)** — 진행 중, 미커밋
- worktree: `.worktrees/20-spec-tourdoum-fe-chat-fe1-keyset-observer`
- 브랜치: `fe/chat-fe1-keyset-observer`
- 상태: **5 파일 staged, 미커밋** (`client.ts / chat.spec.ts / chat.ts / ChatChannelView.vue / ChatView.spec.ts`)
- 다음 회차 첫 처리: fe wake → 진행 상황 확인 → finalize → 머지 → qa #30 Scenario A unblock.

### **be #16 ADR-0011 BE-4 (비밀번호 보안 강화)** — 진행 초기
- worktree: `.worktrees/20-spec-tourdoum-be-jwt-be4-password-security`
- 브랜치: `be/jwt-be4-password-security`
- 상태: **handoff.md 14줄 placeholder만 박제, 코드 작업 미시작 또는 초기**
- 7-box: Argon2id m=64MiB,t=3,p=1 + bcrypt fallback / 12자 / revocation hook (BE-2 family/denylist 재사용) / per-account 5회·30분 + per-IP 10/10 / Reset 30분 1회용 / DelegatingPasswordEncoder rehash on login
- 다음 회차 첫 처리: be wake → 진행 상황 확인 → 보강 필요시 인계 → finalize → 머지.

### qa 미dispatch (대기)
- **qa #30 spec 복원**: fe #14 + be #15(이미 머지) 둘 다 머지 후 `chat-polling.spec.ts` `test.skip → test` 복원 + 재실행 5분 mini-task. 본 회차 spec 골격 박제됨, 활성 시나리오 X.
- **qa #28 BE-4 시나리오** (be #16 머지 후): login 5회 lockout / 30분 자동 해제 / password 변경 family revoke / reset 1회용 / per-IP throttling — handoff §"qa 시나리오 6건"에서 인계 받음.

### ui 미dispatch
- **R10 후보** — Sheet panel scale 추가 / Tabs/Toggle/Badge transition 토큰화 audit / 미캡처 4 라우트(채팅/예약/플랜/`/me`) screenshot. R8 보류한 dev seed route 신설 vs BE+seed 부팅 결정 필요.

### infra 미dispatch
- **INFRA-1+** ADR-0013 v1 박제 후 분기. Kind 멀티노드 + nginx LB / Bucket4j Redis / RabbitMQ FIFO sharded / Redis cluster Lua / MariaDB N shard 등 — `docs/notes/2026-05-07-adr-0013-brainstorm.md` §7 task tree 후보 11+5건.

---

## 3. ADR-0013 brainstorm notes 인계 ⚠️ 핵심

**위치**: `docs/notes/2026-05-07-adr-0013-brainstorm.md` (commit `3c39798`).

**상태**: ADR 박제 보류. 사용자 추가 brainstorm 후 박제 예정.

**필독**: 본 회차에 사용자가 ADR-0013 목표를 정정함:
- 기존: "30만 RPS 처리 아키텍처 설계"
- 정정: **"30만 RPS 실제 재현 + 견뎌내는 아키텍처"** (외부 도구 활용)

researcher #9, #13 두 차례 보고 + Codex critical review 완료. 핵심:
- generator 1순위 = **k6-operator on EKS Seoul** (mixed Graviton spot, 1h <$1)
- 시나리오 A vs B 비교 학습 (예약 폭주 hot path)
- **목표 재정의**: "DB writer 병목 앞에서 A=흡수/제한, B=확장 지연/손실 정량화"
- DB 다중화 7 layer (read replica → vertical → ~~Multi-Master~~ → sharding → Redis Lua token → Kafka WAL)
- 사용자 7개 미해결 결정 중 3개 답변 완료(성공 기준=confirmed / 전체 layer 포함 / ~$10 예산)
- 4개 default 권고: pending=auth 5분 mock, contention=Redis token, WAF=IP+cookie/custom, SQS=FIFO-sharded N=10~20

**비용 시나리오**: 100% 로컬 ($0, ~85% 학습) / 로컬+AWS 1회 30분 burst (~$2~10) / 100% AWS ($200~1000+).

**다음 brainstorm 후보 토픽 8건** notes §8에 박제 — 결제 정책 / Overbooking / Inventory 표현 / Terraform 자동화 / Spot fallback / Karpenter NodePool / k6 script / DB writer 분산 IaC.

다음 회차 architect는 본 노트 정독 후 사용자 brainstorm 추가 → ADR-0013 v1 박제 진행.

---

## 4. 운영 사고 / 처방 (본 회차 패턴)

### `_resolve_base_ref` P0 — ✅ 처방 박제 완료
이전 회차 5회 반복 false-trigger. 본 회차 `.harness c30a36a` 박제: local develop이 origin보다 strict ahead면 local 우선. **본 회차 신규 false-trigger 0건** (BE-3 등 모든 worktree에서 정상). 효과 검증 완료.

### Agent → architect 메시지 race (반복 패턴, 4회)
패턴: agent finalize 보고 송부 → architect가 그 사이 worktree 직접 점검 + 머지 + 다음 dispatch SendMessage → agent가 머지 안내를 새 dispatch로 오해 → "task #N 이미 완료" 회신 → architect 정정 → 정식 진입.

발생: be #1 BE-3 / be #8 BE-1 / be #12 BE-2 / be #15 BE-3 (4회).

**처방 후보** (다음 회차 P1):
- agent prompt에 "architect SendMessage 수신 시 직전 머지 안내인지 새 task인지 명시 확인" 보강
- architect는 머지 안내 + 다음 dispatch를 한 메시지로 합쳐 송부 (분리 송부 race 회피)
- 또는 머지 commit hash + 새 task ID를 항상 같이 인용

### qa stale BE 사고 — 1회 처방 박제
qa #28 첫 시도 시 30080 점유한 다른 worktree(qa-e2e-plans) BE PID 51772/51920 잔존 (14h+, BE-1/BE-2 머지 이전 빌드). architect kill 승인 + qa 본 worktree 재기동 후 PASS.

**처방 후보**: `wt-new.sh`에 BE cleanup hook 또는 architect agent prompt에 "BE 기동 전 30080 점유 PID 검사" 박제.

### researcher → architect 보고 race — 2회 (#6, #13)
researcher가 보고 회수 직전에 idle 송출 → architect가 #6/#13 보고 회수 후 인라인 보고 본문 미수신 상태로 다음 task dispatch → researcher가 "이미 완료" 회신. 본 회차 두 번째 보고는 회수 OK.

처방: researcher.md에 "보고는 항상 SendMessage로 송부, idle 단독 송출 직전 인라인 본문 검증" 박제 권고.

---

## 5. 환경 / 하네스 박제 사항

### `.harness/agent-prompts/researcher.md` 신규 (`fc8a94b`)
- 외부 자료 조사 + 정제 요약(단어 수 제한) + Codex `codex-call.sh` 교차 검토 + architect 인라인 보고 역할.
- 책임 / 금지 / 호출 시 명시 항목 / 함정 박제.
- CLAUDE.md §3 역할 표에 행 추가.

### `_resolve_base_ref` P0 보강 (`.harness c30a36a`) — 위 §4 인용

### infra observability 박제 (`infra #10`)
- `infra/aws/.env.aws.example` placeholder + `infra/aws/README.md` IAM 최소 권한
- `infra/observability/docker-compose.yml` 4 서비스 + Grafana auto-provisioning + Spring Boot overview 대시보드
- backend `application.yml` `management.endpoints.web.exposure.include=prometheus,health,metrics,info`
- `.gitignore` + `.gitleaks.toml` 보강

### AWS Documentation MCP 등록 안내 (사용자 액션 대기)
```bash
claude mcp add --scope user aws-docs uvx -- awslabs.aws-documentation-mcp-server@latest
```
사용자 등록 완료 알림 대기. 등록 후 `mcp__aws-docs__*` 도구로 EKS/Karpenter/HPA/WAF/RDS 공식 문서 직접 회수 가능.

---

## 6. Notion + cl-memory 박제 (BE-4 권고안 단편 포스팅)

본 회차 중간에 BE-4 권고안 + 한국 보안 기준 매핑을 Notion 부모 페이지(358ccedc-...) 11번 sub-page로 박제. 회차 종료 session-log와 별도.

- **Notion 페이지**: https://www.notion.so/359ccedc77098190bfaac914f8de2d13 (참조 이미지: NIST + OWASP wikimedia 공식 로고 임베드)
- **cl-memory 4건**: BE-4 알고리즘 결정 (technical) / 한국 보안 ADR 박제 원칙 (operational) / Researcher teammate 운영 패턴 (operational) / 한국 비밀번호 보안 출처 인덱스 (reference)

회차 종료 session-log는 본 handoff와 함께 별도 박제 예정.

---

## 7. 사용자 push 이슈 (변동 없음)

develop이 origin/develop보다 **141 commits ahead**. 다음 회차 시작 시 사용자가 `git push origin develop` 권장. push 안 하면 다음 회차도 stale base 우려 (P0 처방으로 회피되긴 하나, fork/clone 분기 시 영향).

---

## 8. 미해결 backlog

- **fe #14 ADR-0012 FE-1** — 진행 중 미커밋 (위 §2)
- **be #16 ADR-0011 BE-4** — 진행 초기 (위 §2)
- **qa #30 spec 복원** — fe #14 + be #15 머지 후 5분 mini-task
- **qa BE-4 e2e** — be #16 머지 후 6 시나리오 dispatch
- **ADR-0013 brainstorm 추가 + v1 박제** — 사용자 신호 시 진행
- **AWS Documentation MCP 등록 검증** — 사용자 액션 후 다음 회차 활용
- **R10+** — UI continuous (Sheet scale / Tabs/Toggle/Badge audit / 미캡처 4 라우트)
- **agent message race 처방** (P1) — 4회 반복

---

## 9. 다음 회차 시작 체크리스트

1. `cd 20-spec-tourdoum && git status && git log --oneline -10` (본 회차 27 머지 확인)
2. `git push origin develop` (사용자 권장, 141 ahead)
3. AWS Documentation MCP 등록 검증 (`claude mcp list` 또는 ToolSearch에서 `mcp__aws-docs__*` 노출 여부)
4. tmux teammate 모드 5명 + codex pane 재spawn (be / fe / qa / ui / infra / researcher)
5. 본 SESSION_HANDOFF 정독 → 우선 처리:
   - **fe #14 진행 상황 확인 + 완료** (worktree 5 파일 staged)
   - **be #16 진행 상황 확인 + 완료** (handoff 14줄 placeholder)
   - **머지 후 qa #30 spec 복원 + qa BE-4 e2e dispatch**
   - **사용자 ADR-0013 brainstorm 추가 신호 대기** — `docs/notes/2026-05-07-adr-0013-brainstorm.md` 정독
6. 본 파일 archive (`docs/sessions/2026-05-07-3.md`로 이동) 또는 삭제.

---

## 10. 본 회차 cl-memory 양자화 후보 (회차 종료 session-log에서 박제 예정)

- ADR-0011 cookie+CSRF 흐름 (BE-3 contract 표) — technical
- BE-2 ChatDmCreator REQUIRES_NEW + findFresh InnoDB snapshot 함정 회피 — technical
- last_message guarded UPDATE monotonic (deadlock 학습 포인트) — technical
- ADR-0012 v2 V17 4단계 production 적용 절차 (NULL col → backfill → anomaly 검증 → UNIQUE+CHECK → app 머지) — operational
- BE-3 chat seed runner profile 가드 + 멱등 sentinel 패턴 — technical
- FE-2 X-XSRF-TOKEN auto-injection (`document.cookie` 파싱 + jsdom 가드) — technical
- `_resolve_base_ref` P0 보강 효과 검증 (본 회차 false-trigger 0건) — operational
- ADR-0013 목표 재정의 + DB 7 layer + Codex critical 8항 (researcher #13) — technical
- 100% 로컬 ↔ AWS 도구 매핑 + $10 path breakdown — operational
- agent message race 4회 반복 + 처방 후보 — operational
