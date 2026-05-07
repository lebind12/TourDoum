# ADR 0012 — 채팅 keyset paging + 500만 seed

- 작성일: 2026-05-07
- 상태: Accepted
- 관련: ADR-0004(Flyway), ADR-0011(JWT), ADR-0013 후보(K6 부하)

## 컨텍스트

채팅 도메인(V13)은 현재 forward polling만 구현되어 있다.

```sql
chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_chat_messages_channel_id (channel_id, id)
)
```

- `GET /channels/{id}/messages?sinceId=<long>` → polling용 ASC LIMIT 50.
- "이전 메시지(스크롤 업)" 로딩 endpoint **부재**.
- 메시지 row 수 < 10건 (학습/seed 미박제).

학습 목표:
1. offset → keyset paging 마이그레이션 (대용량에서 OFFSET이 망가지는 이유 + 처방).
2. 복합 인덱스 설계 (정렬·필터·tie-break).
3. 대용량 seed (JDBC batch + `rewriteBatchedStatements` + 트랜잭션 분리).
4. FE cursor contract + 무한 스크롤 race 방지.
5. p50/p95/p99 측정 (ADR-0013 K6 baseline 입력).

## 결정

다음 6가지를 일관 조합으로 채택한다.

| 항목 | 결정 |
|---|---|
| **Endpoint 분리** | forward polling과 backward history 분리. `GET /channels/{id}/messages?afterCursor=` (polling) + `GET /channels/{id}/messages/older?beforeCursor=` (history). 기존 `?sinceId=` endpoint는 호환용으로 한시 유지. |
| **Cursor key** | `(created_at, id)` 복합 cursor. 시간 정렬을 명시하고 동일 timestamp 충돌은 `id`로 tie-break. |
| **인덱스** | 신규 `(channel_id, created_at DESC, id DESC)` 추가 (V17). 기존 `(channel_id, id)`는 polling 호환·rollback 경로로 유지. ADR-0012 완료 후 EXPLAIN 결과로 제거 여부 별도 결정. |
| **500만 seed** | Flyway 미사용. **dev-only JDBC batch runner**. `rewriteBatchedStatements=true` + auto-commit off + 5천~1만 row commit. 채널 20 + 최근 90일 균등. 명시적 profile/argument 없이는 절대 미실행. |
| **FE 계약** | 응답 `{items, nextCursor, hasMore}`. older endpoint는 DB DESC → 응답은 chronological ASC로 반환. FE는 older prepend / polling append. cursor in-flight dedupe + `seenMessageIds` + `exhaustedOlder`. |
| **측정** | 4단계 baseline 박제: ① seed 전 10건 ② 500만 seed 직후 기존 인덱스 ③ 복합 인덱스 추가 후 ④ keyset endpoint 적용 후. 각 단계 EXPLAIN ANALYZE + endpoint p50/p95/p99 + Spring Actuator Timer. |

핵심 원칙: **정렬 기준 + cursor 기준 + 인덱스 prefix가 모두 `(channel_id, created_at, id)` 순서로 동일해야** OFFSET 없이 LIMIT 50 범위만 읽는 구조 보장.

쿼리 패턴:
```sql
-- older (backward, 스크롤 업)
SELECT ... FROM chat_messages
WHERE channel_id = ?
  AND (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT ?;

-- newer (forward, polling)
SELECT ... FROM chat_messages
WHERE channel_id = ?
  AND (created_at, id) > (?, ?)
ORDER BY created_at ASC, id ASC
LIMIT ?;
```

## 근거

### Endpoint 분리 (옵션 B)
polling은 "새 메시지 append", 스크롤 업은 "이전 메시지 prepend"로 클라이언트 의도와 UI 처리가 완전히 다르다. 단일 `direction` endpoint보다 분리된 계약이 디버깅·메트릭 분리·FE race 제어에 유리. 기존 `?sinceId=`는 호환용 유지로 회귀 비용을 분산.

### Cursor `(created_at, id)`
`id` 단독은 현재 AUTO_INCREMENT 구조에서 충분하지만 도메인 의미를 `id`에 의존시킨다. 향후 백필/마이그레이션/외부 import/시각 보정에서 `id` 순서와 메시지 시간이 어긋나면 폭발한다. `(created_at, id)`는 시간 정렬을 명시하고 tie-break를 분리. 학습 의의: keyset paging 핵심 원칙("정렬 조건 = cursor 조건")을 정확히 학습.

### 인덱스 `(channel_id, created_at DESC, id DESC)`
채팅의 주 패턴은 "최신 N건 + 그보다 오래된 N건". MySQL 8 descending index가 자연. older query의 `ORDER BY ... DESC LIMIT 50`에 매핑. newer는 동일 인덱스를 역방향 활용. 기존 `(channel_id, id)`는 polling/rollback 위해 유지 후 제거 검토.

### Seed = JDBC batch runner
seed는 스키마가 아니라 부하 데이터. Flyway repeatable migration에 박지 않는다(ADR-0004 + 이전 V4 mock 사고 트라우마). dev-only runner + 명시 argument로 실수 실행 방지. JdbcTemplate raw batch + `rewriteBatchedStatements=true` + 1만 row 단위 commit으로 학습 가치 + 실 측정 가능.

### FE chronological ASC + race 방지
ChatView가 시간 ASC로 렌더링하므로 응답도 ASC가 안전(prepend/append 단순화). race 방지 요소: `olderLoading`, `olderCursorInFlight`, `exhaustedOlder`, `seenMessageIds`. 같은 `beforeCursor`로 중복 호출 차단 + 늦게 도착한 응답은 id 기준 dedupe. 사용자가 bottom 근처 아니면 새 메시지를 강제 스크롤하지 않고 "새 메시지 있음" UX로 처리.

### 4단계 측정
"데이터 증가 효과" / "인덱스 효과" / "API 계약 변경 효과"를 분리해서 설명 가능. ADR-0013 K6 baseline 입력으로 그대로 활용.

## 결과

### Positive
- 500만 row에서도 OFFSET 없이 LIMIT 50 범위만 read.
- 정렬·cursor·인덱스 일관 — 학습 + 운영 모두 견고.
- Flyway에 부하 데이터 박지 않음 — ADR-0004 무결성 보존.
- ADR-0013 K6 baseline의 정량 입력 확보.

### Negative
- cursor encode/decode + DTO + FE state 변경 → 회귀 비용 중간.
- 인덱스 1개 추가로 write 비용·디스크 사용량 증가(학습 단계 수용 가능).
- ChatView state 구조 변경(`messages`/`olderCursor`/`hasMore`/`seenIds`) → scroll anchoring 테스트 필요.
- 기존 `?sinceId=` 한시 호환 → 일정 기간 후 제거 결정 별도 처리.

## Task 분해 (의존 순서)

```
BE-1 Cursor DTO/API 계약
   ├─ (created_at, id) cursor encode/decode
   ├─ /messages/older endpoint 추가
   └─ 기존 ?sinceId polling 호환 유지
        ↓
BE-2 Query + Index
   ├─ Flyway V17 — (channel_id, created_at DESC, id DESC) 추가
   ├─ older/newer keyset query 구현
   └─ limit + 1 조회 → hasMore 계산
        ↓
BE-3 Local Seed Runner (dev-only JDBC batch)
   ├─ 500만 rows / 20 channels / 90일 분산
   ├─ rewriteBatchedStatements + 1만 row commit
   └─ 실행 결과 + 주의사항 문서화
        ↓
QA-2 Measurement
   ├─ 4단계 p50/p95/p99 + EXPLAIN ANALYZE
   └─ ADR-0013 K6 baseline 입력 정리

— 병렬 라인 —
BE-1 → FE-1 Cursor 기반 ChatView 상태
   ├─ olderCursor / hasMore / in-flight cursor cache
   ├─ prepend 후 scroll position 보존
   └─ polling append + dedupe
        ↓
QA-1 Correctness
   ├─ 같은 timestamp tie-break
   ├─ 중복 호출 / 늦은 응답 / 마지막 페이지
   └─ 기존 polling regression
```

`BE-1 → BE-2 → BE-3 → QA-2`와 `BE-1 → FE-1 → QA-1`을 병렬. cursor contract가 먼저 고정되어야 FE/QA가 흔들리지 않는다.

## 참고

- Codex CLI 회의 로그: `.codex-20260507T*.log` (ADR-0012)
- ADR-0011 (JWT) — 인증 영향 없음 (chat은 `@AuthenticationPrincipal` 사용)
- ADR-0004 (Flyway) — seed는 본 ADR로 명시 분리
- ADR-0013 후보 (K6) — 4단계 baseline이 본 ADR의 출력이자 Group 3 입력
