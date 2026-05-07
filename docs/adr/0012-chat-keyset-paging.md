# ADR 0012 — 채팅 keyset paging + DM uniqueness + 500만 seed (v2)

- 작성일: 2026-05-07 (v1) / **개정 2026-05-07 (v2)**
- 상태: Accepted (v2)
- 관련: ADR-0004(Flyway), ADR-0011(JWT), ADR-0013 후보(K6 부하)

## 컨텍스트

채팅 도메인(V13)은 forward polling만 구현 + 인덱스/스키마에 다음 미해결 요소가 있다.

```sql
chat_messages (id PK, channel_id, sender_id, content TEXT, created_at, INDEX (channel_id, id))
chat_members  (PRIMARY KEY (channel_id, member_id), last_read_message_id, ...)
chat_channels (id PK, name, type ENUM('PUBLIC','DM'), created_at)
```

문제 지점 (사용자 건의 + 코드 점검):

1. **이전 메시지 페이지네이션 부재** — `?sinceId=` polling만. backward(스크롤 업) 없음.
2. **`chat_members` PK = `(channel_id, member_id)`** → `member_id` 단독 lookup이 PK leftmost 위반 = **full scan**. `findMyChannels(memberId)`, `findDmChannel(a,b)` 모두 영향.
3. **DM 채널 uniqueness 부재** — 같은 두 사용자에 대해 동시 `openDm` race로 채널 중복 생성 가능.
4. **채팅방 목록 N+1** — "내 채널" + "각 채널 마지막 메시지 미리보기"는 채널당 별 쿼리.
5. **PUBLIC + DM cardinality 차이 측정 부재** — 단일 시나리오로는 두 사용 패턴 진단 어려움.
6. **최적화 전후 정량 측정 부재** — 자동화 회귀 측정 도구 없음.

학습 목표:
1. offset → keyset paging (대용량 OFFSET 한계 + 처방).
2. PK leftmost 원칙과 `(member_id, channel_id)` 역방향 인덱스.
3. 도메인 제약(DM uniqueness)을 application lock이 아닌 DB constraint로 끌어올리기.
4. denormalization trade-off (read 빠름 vs write contention).
5. 자동화된 회귀 벤치 + JWT 비용 분리 측정.
6. ADR-0013 K6 baseline의 정량 입력 박제.

## 결정

다음 7가지를 일관 조합으로 채택한다 (v2 개정).

| # | 항목 | 결정 |
|---|---|---|
| 1 | **Endpoint 분리** | forward `?afterCursor=` (polling) + backward `/messages/older?beforeCursor=` (history). 기존 `?sinceId=` 한시 호환. |
| 2 | **Cursor key** | `(created_at, id)` 복합 cursor. 시간 정렬 명시 + tie-break. |
| 3 | **메시지 인덱스** | 신규 `(channel_id, created_at DESC, id DESC)` (V17). 기존 `(channel_id, id)`는 polling 호환·rollback 위해 한시 유지. |
| 4 | **chat_members 역방향 인덱스 (P0)** | `idx_chat_members_member (member_id, channel_id)` (V17). PK는 `(channel_id, member_id)` 그대로 유지. |
| 5 | **DM pair UNIQUE (P0)** | `chat_channels.dm_member_min/max` 컬럼 + UNIQUE INDEX + strict CHECK. backfill + anomaly 검출 후 적용. PUBLIC은 두 컬럼 NULL. |
| 6 | **last_message denormalize (조건부)** | `chat_channels.last_message_id/at` 컬럼 + `(last_message_at DESC, id DESC)` 인덱스. backfill은 `ROW_NUMBER()`로. **PUBLIC hot channel write contention 측정 후 strict transactional update vs asynchronous write-behind 확정**. |
| 7 | **Seed + 측정 자동화** | dev-only JDBC batch runner, 시나리오 분리(PUBLIC heavy / DM many / mixed) + Zipf/skew 분포. mvn `-Pchat-bench` profile + 4단계 baseline + warm-up 강제 + JWT 비용 분리 측정. |

핵심 원칙: **정렬 기준 + cursor 기준 + 인덱스 prefix가 모두 `(channel_id, created_at, id)` 순서로 동일**.

### 쿼리 패턴

```sql
-- older (backward, 스크롤 업)
SELECT ... FROM chat_messages
WHERE channel_id = ? AND (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC LIMIT ?;

-- newer (forward, polling)
SELECT ... FROM chat_messages
WHERE channel_id = ? AND (created_at, id) > (?, ?)
ORDER BY created_at ASC, id ASC LIMIT ?;

-- findMyChannels (JOIN 패턴, IN 서브쿼리 폐기)
SELECT c.* FROM chat_members cm
JOIN chat_channels c ON c.id = cm.channel_id
WHERE cm.member_id = ?
ORDER BY c.last_message_at DESC, c.id DESC;

-- findDmByMemberPair (옵션 B 신규)
SELECT * FROM chat_channels
WHERE dm_member_min = LEAST(?, ?) AND dm_member_max = GREATEST(?, ?);
```

### LIMIT default = 20, BE clamp `1 ≤ limit ≤ 50`

사용자 시나리오(스크롤 위로 10~20개) 일치. PUBLIC fill rate 부족 시 FE가 viewport에 따라 initial만 30~40 요청 가능(서버 max 50 유지). content length 상한도 별도 박제 필요(`Validator @Size(max=...)`).

### V17 마이그레이션 순서 (P0 강제)

```
columns 추가
  ↓
backfill (last_message: ROW_NUMBER ORDER BY created_at DESC, id DESC LIMIT 1)
  ↓
anomaly 검출 (DM 멤버 ≠ 2 / pair 중복 / NULL/NOT NULL 정합성)
  ↓
UNIQUE INDEX 추가
  ↓
strict CHECK constraint 추가
  ↓
검증 쿼리 통과 후 commit
```

검증 쿼리 (마이그레이션 후 0건 확인):
```sql
-- 1. type='DM'인데 멤버 ≠ 2
SELECT c.id FROM chat_channels c
WHERE c.type = 'DM' AND (
  SELECT COUNT(*) FROM chat_members WHERE channel_id = c.id
) != 2;

-- 2. type='DM'인데 pair NULL
SELECT id FROM chat_channels WHERE type = 'DM' AND (dm_member_min IS NULL OR dm_member_max IS NULL);

-- 3. type<>'DM'인데 pair NOT NULL
SELECT id FROM chat_channels WHERE type <> 'DM' AND (dm_member_min IS NOT NULL OR dm_member_max IS NOT NULL);

-- 4. pair 중복
SELECT dm_member_min, dm_member_max, COUNT(*) FROM chat_channels
WHERE type = 'DM' GROUP BY dm_member_min, dm_member_max HAVING COUNT(*) > 1;
```

### strict CHECK constraint

```sql
ALTER TABLE chat_channels
  ADD CONSTRAINT chk_chat_channels_dm_pair CHECK (
    (type = 'DM' AND dm_member_min IS NOT NULL AND dm_member_max IS NOT NULL AND dm_member_min < dm_member_max)
    OR
    (type <> 'DM' AND dm_member_min IS NULL AND dm_member_max IS NULL)
  );
```

### `openDm` 동시성 — JPA save() catch는 취약

Codex 지적: JPA `save()`의 flush 지연 + 같은 transaction 재조회 취약. 권장 대안:

**옵션 1 (권장)**: native upsert
```sql
INSERT INTO chat_channels (name, type, dm_member_min, dm_member_max, created_at)
VALUES (?, 'DM', ?, ?, NOW(6))
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);
-- 후 SELECT WHERE dm_member_min = ? AND dm_member_max = ?
```

**옵션 2**: 생성 시도를 별 transaction(`Propagation.REQUIRES_NEW`)으로 분리 + 외부 transaction에서 재조회.

ChatService.openDm 본 task 구현 단계에 둘 중 선택 + plan.md 박제.

### last_message denormalization — 조건부

**P1 검토 절차**:
1. 먼저 `chat_messages(channel_id, created_at DESC, id DESC)` 인덱스로 **batch latest 조회** N+1 제거 가능 여부 측정.
2. denorm 채택 시 **monotonic guarded UPDATE**:
   ```sql
   UPDATE chat_channels
   SET last_message_id = ?, last_message_at = ?
   WHERE id = ?
     AND (last_message_at < ? OR (last_message_at = ? AND last_message_id < ?));
   ```
3. PUBLIC hot channel(예: 5000명) → outbox/write-behind eventual update.
4. DM/small channel → 같은 transaction strict update.

본 ADR은 컬럼 + 인덱스 + backfill까지 박고, **strict vs write-behind는 측정 결과 따라 BE-2 확정**으로 명시.

### 측정 자동화 (mvn -Pchat-bench)

표준 절차:
1. **warm-up 20~50회 discard** — Spring context, Hikari pool, prepared statement, MySQL buffer pool.
2. **N=1000 이상**으로 p99 산출 (N=100은 p50/p95 smoke만).
3. **SQL microbench / HTTP endpoint bench / K6 scenario bench 분리**:
   - SQL = JdbcTemplate 직접, JWT 제외, DB/index 효과만.
   - Endpoint = Spring Security + JWT + Jackson 포함.
   - K6 = ADR-0013 범위.
4. **rows examined 정의** — slow query log 또는 session `Handler_read%` 기준 명시. EXPLAIN ANALYZE는 iterator timing/rows 별도.
5. **결과 박제 위치**:
   - `docs/load-test/chat-paging/<run-id>/{summary.md, explain.txt, latency.csv}` (commit).
   - 큰 raw 파일은 `.gitignore`.
6. **비교표 표준**:
   ```
   | Stage | Scenario | Tag | p50 | p95 | p99 | rows examined | index used | delta vs baseline |
   ```

### Seed 시나리오 (Zipf + membership skew)

`--scenario` argument 분리:
- **public-heavy** — 50 PUBLIC 채널 + Zipf 분포(1 hot + long tail) + 총 250만 메시지 + 1 hot channel에 1000~5000명 멤버.
- **dm-many** — 500 DM 채널 + 채널당 5000 메시지 + membership skew(한 사용자 100 DM, 상위 사용자 1000 DM).
- **mixed** — 위 둘 합본. 단 보고는 **시나리오/태그별 분리**, 단일 평균 X.
- 추가 query 태그: `list-my-channels`, `older-page`, `newer-poll`, `open-dm-race`.

### JWT 비용 분리 (ADR-0011 영향)

> ADR-0012의 SQL benchmark는 JWT 비용을 제외한다. ADR-0013 K6 endpoint benchmark는 RS256 검증, Spring Security filter, JSON serialization을 포함한다. **두 결과를 분리 기록한다**.

ADR-0012 v1의 "JWT 인증 영향 없음" 문구는 v2에서 위로 갱신.

## 근거 (변경된 부분만)

### chat_members `(member_id, channel_id)` 인덱스 (P0)
PK `(channel_id, member_id)`는 `channel_id` prefix 친화. `member_id` 단독은 leftmost 위반 → full scan. 1M `chat_members` 기준 인덱스 적용 후 lookup이 평균 K(내 채널 수)만 — **약 10,000배 감소**. `findMyChannels`, future unread/mute/read receipt에 모두 필요.

### DM pair UNIQUE는 application lock보다 강함
- application synchronized: 단일 인스턴스만. 분산(ADR-0013)에선 race 누수.
- 분산 lock(Redisson 등): 운영 부담 + 학습 단계 과함.
- DB UNIQUE: race를 DB level에서 차단. 학습 가치 + 분산 안전.

### last_message denorm — 조건부
PUBLIC 5000명 채널에서 모든 INSERT가 같은 row UPDATE → row lock 직렬화 → send p99 망가짐. 학습 단계에 가장 위험한 변경. 측정 후 strict/write-behind 확정.

## 결과

### Positive
- 500만 row에서 OFFSET 없이 LIMIT 20~50만 read.
- DM uniqueness DB level 보장 + race 차단.
- chat_members `member_id` 단독 lookup full scan 제거 (~10,000배).
- N+1 회피 (조건부 + monotonic UPDATE).
- 자동화 회귀 측정 + JWT 비용 분리 + 시나리오/태그별 보고.
- ADR-0013 K6 baseline 정량 입력 확보.

### Negative
- V17 마이그레이션 단계 다수 + anomaly 검출 절차 필수.
- last_message denorm은 PUBLIC hot에서 측정 결과에 따라 변경 가능성.
- 인덱스 3개 추가 → write 비용·디스크 사용량 증가.
- ChatView state 변경 + scroll anchoring 검증.
- `?sinceId=` 한시 호환 후 제거 결정 별도.
- benchmark 절차 표준화 비용 (warm-up, N≥1000, 분리 보고).

## 범위 밖 — 별 ADR 후보 명시

본 ADR은 다음을 다루지 않는다. 후속 ADR 후보로 박제.

| 항목 | 사유 | 후보 |
|---|---|---|
| **read receipt / unread count** | `last_read_message_id`(V13)로 가능하지만 별 설계 필요 | ADR-0014 후보 |
| **메시지 검색** | `LIKE '%w%'` **금지**. MySQL FULLTEXT/ngram/Elasticsearch/OpenSearch 별 결정 | ADR-0015 후보 |
| **메시지 삭제/수정** | 본 ADR 범위에서 **immutable** 명시. soft delete 들어오면 last_message denorm 즉시 깨짐 | ADR-0016 후보 |
| **차단/뮤트 / fan-out unread / per-member mute / read receipt 집계** | PUBLIC 5000명 규모 별 설계 필요 | ADR-0017 후보 |

## Task 분해 (v2 의존 순서)

```
BE-1 Cursor DTO/API 계약
   ├─ (created_at, id) cursor encode/decode
   ├─ /messages/older endpoint 추가 + LIMIT clamp 1≤?≤50
   ├─ content length @Size 검증
   └─ ?sinceId 한시 호환
        ↓
BE-2 V17 + Query/Index/Repository
   ├─ V17 (단계 마이그레이션 + anomaly 검출)
   │   ├─ chat_messages 인덱스
   │   ├─ chat_members 역방향 인덱스
   │   ├─ chat_channels DM pair (column→backfill→anomaly→UNIQUE→CHECK)
   │   └─ chat_channels last_message (column→backfill ROW_NUMBER→인덱스)
   ├─ findMyChannels JOIN 패턴
   ├─ findDmByMemberPair + openDm native upsert OR 별 transaction
   └─ ChatService.send last_message guarded UPDATE (또는 batch latest 측정 후 결정)
        ↓
BE-3 Local Seed Runner
   ├─ --scenario=public|dm|mixed
   ├─ Zipf + membership skew
   └─ 실행 결과 + 주의사항 문서화
        ↓
QA-2 Measurement
   ├─ mvn -Pchat-bench profile (warm-up + N≥1000)
   ├─ SQL/Endpoint/K6 분리 보고
   ├─ JWT 비용 분리
   ├─ 4단계 baseline + 시나리오/태그별
   └─ ADR-0013 K6 baseline 입력

— 병렬 라인 —
BE-1 → FE-1 Cursor 기반 ChatView 상태
   ├─ olderCursor / hasMore / in-flight cursor cache
   ├─ prepend 후 scroll position 보존
   ├─ polling append + dedupe
   └─ initial 30~40 요청 viewport 적응
        ↓
QA-1 Correctness
   ├─ same timestamp tie-break
   ├─ 중복 호출 / 늦은 응답 / 마지막 페이지
   ├─ openDm 동시성(2 thread + DB UNIQUE 차단)
   └─ ?sinceId regression
```

## 권장 ADR 문구 (Codex 인용)

```text
V17은 DM pair uniqueness와 chat_members reverse lookup을 P0로 적용한다.
last_message denormalization은 PUBLIC hot channel write contention 측정 결과에 따라
strict transactional update 또는 asynchronous write-behind 중 하나로 확정한다.

Benchmarks are split into:
1. SQL/JdbcTemplate benchmark: no JWT, no HTTP, DB/index effect only.
2. Authenticated endpoint benchmark: includes Spring Security/JWT/Jackson.
3. K6 scenario benchmark: PUBLIC heavy, DM many, mixed are tagged and reported separately.
```

## 참고

- v1 → v2 개정 회의: 사용자 건의 3건(측정 자동화, PUBLIC/DM 분리, DM full scan) → architect 안 → Codex critical review → P0/P1/P2 분류 → v2 박제.
- Codex CLI 회의 로그: `.codex-20260507T*.log` (ADR-0012 v1 + v2 review).
- ADR-0011 (JWT) — 기능 영향 없음, 성능 영향 분리 측정.
- ADR-0004 (Flyway) — seed는 본 ADR로 명시 분리.
- ADR-0013 후보 (K6) — 본 ADR 4단계 baseline + 시나리오 태그가 입력.
- 외부 참고:
  - [MySQL CREATE INDEX](https://dev.mysql.com/doc/refman/8.0/en/create-index.html)
  - [MySQL CHECK constraints](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html) (8.0.16+ enforce)
  - [MySQL EXPLAIN](https://dev.mysql.com/doc/refman/8.0/en/explain.html)
