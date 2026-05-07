# SESSION_HANDOFF — TourDoum (2026-05-07 EOS, 2회차)

다음 회차 architect 재개용 박제. 본 파일은 일회용 — 다음 회차 architect가 정독 후 archive(`docs/sessions/2026-05-07-2.md`로 이동) 또는 삭제.

---

## 1. 본 회차 develop 머지 (커밋 순)

### qa e2e 큐 (4건)
- #18 Accommodations e2e 8/8 (origin host 통일 + #39 랜딩 selector)
- #20 Favorites e2e 4/4
- #22 Reviews e2e 4/4 + `signupAndLogin` 헬퍼 추출
- #24 Reservations e2e 4/4 + Idempotency-Key 멱등성
- #26 Plans e2e 5/5 (drag UI 한계 박제, API reorder 대체)

### BE follow-up (2건)
- #41 Review.rating TINYINT → INT (Hibernate 6.5+ schema-validation 차단 해소)
- #43 Accommodation sido/gugun 컬럼 + V16 + regions endpoint
- #48 Accommodation detail DTO 보강 (V15: imageUrl/amenities/maxGuests/checkIn/Out/reviewCount + last_message 미포함)

### FE 처리
- #49 Reservation 라우터 가드 fix (`reservations.confirmed.find` TypeError → `lastConfirmed/myReservations` fallback)
- #50 Accommodation 필터 활성화 (sido/gugun + regions endpoint 연결 + 캐시)

### UI continuous (2건)
- Round 4 G — 컴포넌트 카탈로그 `/dev/ui-catalog` (DEV-only 라우트)
- Round 5 J — 다크모드 contrast audit (토큰 4개 개선)
- Round 6 I — 키보드 a11y (Sheet/AlertDialog focus trap + return + Tabs focus-visible)

### 하네스 + ADR (다수)
- **78b3515** agent 포트 분리 (BE 30080 / FE 30173 / e2e 30174). 사용자 로컬 8080/5173/5174 보호.
- **b263ad6** ADR-0011 JWT 인증 전환 + ADR-0003 superseded
- **dbbff0d** be #61 (BE-1) — Spring Session 폐기 + JWT RS256 골격 + jjwt 0.12.6
- **fe #62** (FE-1) — JWT Bearer 호환층 + Pinia auth store + axios interceptor + 401 redirect
- **c46da70** ADR-0012 v1 (채팅 keyset paging + 500만 seed)
- **778676e** ADR-0012 v2 — Codex critical review 반영 (P0/P1/P2)

### 부수
- gitleaks allowlist 보강 (qa #24 idempotency UUID + JWT dev pem)
- 33 + 28 commits over develop (총 70+ ahead of origin)

---

## 2. ⚠️ 진행 중 task (다음 회차 첫 처리)

### **be #63 (ADR-0011 BE-2 token/Redis)** — ✅ 작업 완료, 머지 대기
- worktree: `.worktrees/20-spec-tourdoum-be-jwt-be2-token-redis`
- 브랜치: `be/jwt-be2-token-redis`, 커밋 `64f5548` (27 files: 신규 15 + 수정 9 + test 8)
- 게이트:
  - ✅ `mvn verify -DskipITs` PASS — auth 21/21 (Controller 6 + Service 5 + Filter 4 + Provider 3 + ProviderRefresh 3) + 전체 비-IT 그린
  - ✅ spotless 통과
  - ❌ FE smoke FAIL — false-trigger 5회째, `--skip-build` 미승인 상태로 회차 종료
- 구현 완료 항목:
  - jjwt RS256 동일 키, refresh ttl 14d
  - family Redis key `auth:refresh:{family_id}` JSON value
  - denylist `auth:denylist:{jti}` 자동 만료
  - AuthService(rotate / replay 감지 / family 폐기) + AuthController `/auth/refresh` `/auth/logout`
  - JwtAuthenticationFilter denylist 매 요청 체크 + jti/expiresAt request attribute
  - previous-kid 활성: 발급은 active만, 검증은 active + previous
  - Redis 다운 silent fallback 금지 (StringRedisTemplate 예외 전파)
  - prevJti grace 정책 채택 (네트워크 race 1회 grace, 학습 친화)
  - 슬라이스 InMemory `@Import(InMemoryAuthTestConfig.class)` swap
  - IT는 Testcontainers Redis로 rotation/replay/logout 시나리오 검증
- 다음 회차 첫 처리:
  1. `--skip-build` Architect 승인 + 머지 (BE-only, BE 게이트 PASS, FE 무관 — #41/#48/#43/#61과 동일 사유, 5회째)
  2. **머지 즉시 FE 깨짐** (refreshToken body 추가 + logout 인증 필수) → FE-1 보강(refresh interceptor)을 인접 dispatch 권장
  3. handoff.md 정독: prevJti grace 정책 / Redis 키 구조 / contract 변경표 / 후속 큐
- 미해결 결정 포인트:
  - BE-3 cookie 전환 시 body refreshToken 노출 여부 (ADR-0011 권장=cookie, 본 BE-2는 body 응답)
  - grace 정책 엄격화 여부 (현재 1회 grace)

### ADR-0012 v2 BE/FE 작업 — 미dispatch
v2 박제 완료, BE/FE 분해 8개 task 모두 미dispatch:
- BE-1 Cursor DTO + /messages/older endpoint + LIMIT clamp + content @Size
- BE-2 V17 (단계 마이그레이션) + JOIN/findDmByMemberPair + last_message guarded UPDATE
- BE-3 dev-only seed runner (--scenario=public|dm|mixed + Zipf + skew)
- FE-1 ChatView keyset state + IntersectionObserver + dedupe
- QA-1 correctness (tie-break / 늦은 응답 / openDm 동시성)
- QA-2 measurement (mvn -Pchat-bench, warm-up + N≥1000, JWT 분리)

dispatch 우선순위: ADR-0011 JWT BE-2 머지 → BE-3 CSRF/cookie → FE-1 보강(refresh interceptor) → QA-1 → ADR-0012 BE-1.

### qa 큐 — 미dispatch
- #28 Notifications e2e (트리거 + 읽음)
- #30 Chat 폴링 e2e

ui 큐 — 미dispatch
- Round 7 H 반응형 < 640px / K 마이크로 인터랙션 / `/me` mock-fixture로 R5 보강

---

## 3. 운영 사고 / 처방 (반복 패턴)

### `_resolve_base_ref` false-trigger — 4회 반복 (P0)
사용자가 `git push origin develop` 안 하는 환경에서 origin/develop가 develop보다 70+ 커밋 뒤. agent의 `agent-finalize.sh`가 `origin/develop`을 base로 잡아 무관한 frontend 변경 누적분이 diff에 들어와 FE 스모크 false-trigger.

발생 회차: be #41 / be #48 / be #43 / be #61 (BE-1).

**다음 회차 P0 처리 후보**:
1. `_resolve_base_ref()` 보강 — local develop이 origin/develop보다 ahead면 local 우선.
2. agent prompt에 명시적 base ref 인자 추가.
3. 사용자에게 `git push origin develop` 권장(이번 회차도 push 미진행, develop이 origin보다 ~110 커밋 앞).

**임시 처방 (이번 회차 사용)**: `--skip-build` Architect 명시 승인 + handoff 인용 박제. 4회 반복.

### handoff race condition
finalize 후 handoff.md 갱신 → idle 송출 직후 architect가 worktree 점검하는 타이밍 race로 빈 handoff 본 사고 1회 (be #41). 처방: handoff는 finalize 직전에 갱신 + 보고 메시지에 인라인 포함 (be가 적용 동의).

### idle 단독 송출 — 운영 규칙 반복 위반
qa는 본 회차에 #18/#20/#22/#24/#26 모두 텍스트 보고 누락 + idle만 송출. handoff는 정상 박제. team-lead가 매번 worktree 직접 점검 → 비효율. 다음 회차 sub-agent prompt에 더 강한 강제 또는 hook 도입 검토.

### gitleaks false positive — UUID 멱등성 키
qa #24 머지 시 idempotency UUID가 generic-api-key 룰에 잡힘. allowlist 보강 (`docs/screenshots/*.json` + `idempotencyKey":\s*"[a-f0-9-]{36}"`).

### biome auto-fix 머지 충돌 패턴
ui Round 1 머지에서 biome auto-fix와 develop 변경이 충돌 4건. architect 직접 해결 + 산출물 develop leak 정리.

---

## 4. 환경 / 하네스 박제 사항

### 포트 분리 (78b3515 + .harness 0094a16)
- 사용자 로컬: 8080 / 5173 / 5174
- agent worktree: 30080 / 30173 / 30174
- `wt-new.sh`가 `.env.agent` 자동 박제 (backend/frontend 양쪽).
- agent는 backend/frontend 띄울 때 `set -a; . .env.agent; set +a` source 후 실행.
- BE CORS allowed-origins default에 30173/30174 포함됨.

### Codex CLI 단독 정책
- `codex-call.sh`의 `OPENAI_API_KEY` 가드 제거 — `codex login`(chatgpt) 우선.
- `.harness/scripts/README.md`에 "Codex는 Codex CLI 단독 사용. OpenAI API 직접 호출 금지" 명시.
- 본 회차에 ADR-0011 brainstorm + ADR-0012 v1 brainstorm + ADR-0012 v2 critical review + ADR-0013 brainstorm 4회 호출. 모두 정상.

### tmux 마우스 모드
`~/.tmux.conf`에 `set -g mouse on` 박제. 사용자 활성 swarm socket에 즉시 적용.

### settings.json (이전 회차 박제)
Playwright MCP 22개 도구 영구 허용 — UI/QA agent 권한 prompt 차단.

---

## 5. ADR-0013 brainstorm 결과 (다음 세션 박제 예정)

본 회차에 사용자 요청으로 brainstorm은 진행했으나 사용자 결정 = "다음 세션에서 다시 검토" → ADR 박제 보류. brainstorm 결과는 Codex 회의 로그(`.codex-20260507T*.log`)에 박제됨. 다음 회차에서 사용자 검토 후 ADR-0013 박제 결정.

핵심 권고 요약:
- 30만 RPS는 **로컬 달성 X, "도달 경로 산정 목표"**.
- JSON summary + Prometheus 보조 / `ramping-arrival-rate` + `constant-arrival-rate`.
- core 5 → 9 전체 + read 80~90%/write 10~20%.
- 튜닝 순서: Hikari/Tomcat/MySQL/JVM (RS256→HS256 보류).
- nginx + BE N=2/4/8, sticky session 없음.
- 30만 path: `required_instances = ceil(target / (measured × safety_factor 0.65~0.75))`. 5k RPS · 0.7 → 86대 ($6k+/month).

---

## 6. 사용자 push 이슈 (변동 없음)

develop이 origin/develop보다 ~110+ 커밋 앞. 다음 회차 시작 시 사용자가 `git push origin develop` 권장. 안 하면 false-trigger 4회 패턴 5회로 늘어남.

---

## 7. 미해결 backlog

- **`_resolve_base_ref` 보강** (P0) — false-trigger 4회 반복.
- **agent-init.sh `.git/info/exclude` 등록** — 산출물 develop 누수 패턴 (이번 회차에도 산발 발생).
- **idle 단독 송출 hook 강제** — qa 텍스트 보고 누락 패턴.
- **be #63 BE-2 token/Redis 머지** — 진행 중.
- **ADR-0011 후속**: BE-3 CSRF/cookie / BE-4 비밀번호 강화 / FE-1 refresh interceptor 보강 / QA-1 e2e 헬퍼 갱신.
- **ADR-0012 v2 후속**: BE-1~3 + FE-1 + QA-1/2 (8 task).
- **ADR-0013** 박제 + Group 3 진입.
- **#34 UI Round 7+** — H/K/`/me` mock.
- **qa #28 Notifications / #30 Chat 폴링 e2e**.

---

## 8. 다음 회차 시작 체크리스트

1. `cd 20-spec-tourdoum && git status && git log --oneline -10` (본 회차 머지 확인)
2. `git push origin develop` (사용자 권장)
3. `tmux -L claude-swarm-<port> ls` 또는 새 팀 spawn
4. 4명 teammate 재spawn (be / fe / qa / ui) — prompt 그대로
5. codex pane 재attach: `.harness/scripts/tmux-attach-codex-pane.sh`
6. 본 SESSION_HANDOFF.md 정독 → 우선 처리:
   - **be #63 BE-2 진행 상황 확인 + 머지** (worktree에서 보고 회수)
   - **`_resolve_base_ref` 보강** (P0)
   - **ADR-0013 검토 + 박제**
   - **ADR-0012 BE-1 dispatch** (Cursor DTO/API + /messages/older)
   - **ADR-0011 BE-3 CSRF/cookie dispatch** (BE-2 머지 후)
7. 본 파일 archive (`docs/sessions/2026-05-07-2.md`로 이동) 또는 삭제.
