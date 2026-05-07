# handoff — be/notifications-domain

## 완료 내용

- `GET /api/notifications` — 알림 목록 (최신순 페이징, page/size 파라미터)
- `GET /api/notifications/unread-count` — 미읽음 알림 수 (폴링용)
- `POST /api/notifications/{id}/read` — 단건 읽음 처리 (403 권한 체크)
- `POST /api/notifications/read-all` — 전체 읽음 처리
- `publish()` 내부 트리거:
  - `ReviewService.create()` → `REVIEW_REPLY` 알림 발행
  - `ReservationService.confirm()` → `RESERVATION_CONFIRMED` 알림 발행
- Flyway V12: notifications 테이블 + 복합 인덱스 `(member_id, read_at)`
- GlobalExceptionHandler: NotificationNotFound(404), NotificationForbidden(403) 추가
- Mockito 단위테스트 2종 (권한 체크 403, 정상 읽음 처리)

---

## FE 연결 가이드 (Task #27 — FE-wire-6)

### 1. FE store `notifications.ts` 필드 매핑

| FE store 필드 | BE 응답 필드 | 변환 |
|---|---|---|
| `id` (string) | `id` (Long) | `res.id.toString()` |
| `type` (소문자) | `type` (대문자) | `.toLowerCase()` |
| `title` | `title` | 동일 |
| `message` | `body` | 필드명 다름: `body` |
| `link` | `linkUrl` | 필드명 다름: `linkUrl` |
| `read` (boolean) | `unread` (boolean) | `!unread` |
| `createdAt` | `createdAt` | ISO 동일 |

### 2. 폴링 패턴 (FE 권장)

```typescript
// 5-30초 주기로 unread-count 폴링
let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  pollTimer = setInterval(async () => {
    const { count } = await $fetch('/api/notifications/unread-count')
    store.unreadCount = count
  }, 15_000)  // 15초
}
function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
}
```

### 3. 알림 목록 조회

```typescript
// GET /api/notifications?page=0&size=20
const result = await fetch('/api/notifications?page=0&size=20').then(r => r.json())
// result: { content: [...], page, size, totalElements, totalPages, last }

// FE 변환
const storeItems = result.content.map(n => ({
  id: n.id.toString(),
  type: n.type.toLowerCase(),   // 'REVIEW_REPLY' → 'review_reply'
  title: n.title,
  message: n.body,              // BE 'body' → FE 'message'
  link: n.linkUrl,              // BE 'linkUrl' → FE 'link'
  read: !n.unread,              // BE 'unread' 반전 → FE 'read'
  createdAt: n.createdAt,
}))
```

### 4. 읽음 처리

```typescript
// 단건
await fetch(`/api/notifications/${id}/read`, { method: 'POST' })

// 전체
await fetch('/api/notifications/read-all', { method: 'POST' })
```

### 5. Pinia store 교체 패턴

```typescript
// src/stores/notifications.ts
actions: {
  async fetchList(page = 0) {
    const res = await $fetch(`/api/notifications?page=${page}&size=20`)
    this.notifications = res.content.map(this.toStoreItem)
    this.hasMore = !res.last
  },
  async fetchUnreadCount() {
    const { count } = await $fetch('/api/notifications/unread-count')
    this.unreadCount = count
  },
  async markRead(id: string) {
    await $fetch(`/api/notifications/${id}/read`, { method: 'POST' })
    const n = this.notifications.find(n => n.id === id)
    if (n) n.read = true
    this.unreadCount = Math.max(0, this.unreadCount - 1)
  },
  async markAllRead() {
    await $fetch('/api/notifications/read-all', { method: 'POST' })
    this.notifications.forEach(n => (n.read = true))
    this.unreadCount = 0
  },
  toStoreItem(n: NotificationResponse) {
    return {
      id: n.id.toString(),
      type: n.type.toLowerCase(),
      title: n.title,
      message: n.body,
      link: n.linkUrl,
      read: !n.unread,
      createdAt: n.createdAt,
    }
  },
}
```

---

## 미해결 질문 / Reviewer 검토 포인트

1. **publish 트리거 학습 모드**: 현재 후기 작성자가 자신에게 알림을 받는다. 실제론 대상 숙박/관광지 오너에게 발행해야 하나 오너 개념이 없어 학습 모드로 처리.
2. **페이징 응답 구조**: `PageResponse`가 `attraction` 패키지에 있음 — 향후 `common` 패키지로 이동 권장 (follow-up).
3. **알림 삭제 엔드포인트 없음**: 스펙 없어서 미구현. 필요 시 추가 요청.
4. **폴링 주기**: FE와 협의 필요. 현재 권장값 15초.
