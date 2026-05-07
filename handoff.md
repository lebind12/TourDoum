# handoff — be/chat-domain (Task #16 BE-7)

## 완료 내용

- V13__chat.sql: chat_channels, chat_members(복합PK), chat_messages 마이그레이션
- ChatChannel, ChatMember(@IdClass), ChatMessage 엔티티
- ChatChannelRepository: findMyChannels(JPQL), findDmChannel(JPQL)
- ChatMessageRepository: sinceId 폴링 (findTop50ByChannelIdAndIdGreaterThan...)
- ChatService: listMyChannels, messages(sinceId), send, openDm(자동생성/중복방지)
- ChatController: 4 엔드포인트 (SESSION 인증, Swagger 박제)
- GlobalExceptionHandler: Chat 404(ChatChannelNotFoundException), 403(ChatForbiddenException) 추가
- ChatServiceTest: DM 자동생성/중복방지/자기자신 예외 3종 (43/43 pass)

---

## FE store 연결 가이드 (Task #29 — FE-wire-7)

### API 엔드포인트

| 메서드 | URL | 설명 |
|---|---|---|
| GET | `/api/chat/channels` | 내 채널 목록 (인증 필수) |
| GET | `/api/chat/channels/{id}/messages?sinceId=0` | 채널 메시지 폴링 |
| POST | `/api/chat/channels/{id}/messages` | 메시지 전송 `{ content: string }` |
| POST | `/api/chat/dm` | DM 채널 열기 `{ otherMemberId: number }` |

### 폴링 패턴 (1~3초 주기)

```typescript
// chat store (Pinia)
async function pollMessages(channelId: number, sinceId: number) {
  const msgs = await $fetch(`/api/chat/channels/${channelId}/messages`, {
    params: { sinceId }
  })
  if (msgs.length > 0) {
    messages.value.push(...msgs)
    lastSinceId.value = msgs[msgs.length - 1].id
  }
}
// setInterval(() => pollMessages(channelId, lastSinceId.value), 2000)
// onUnmounted(() => clearInterval(timer))
```

### 응답 타입

```typescript
interface ChatChannelResponse {
  id: number
  name: string
  type: 'PUBLIC' | 'DM'
  createdAt: string
}

interface ChatMessageResponse {
  id: number
  channelId: number
  senderId: number
  content: string
  createdAt: string
}
```

### 에러 코드

| 상태 | 의미 |
|---|---|
| 401 | 미인증 |
| 403 | 채널 구성원 아님 |
| 404 | 채널 미존재 |
| 400 | 자기 자신에게 DM / content 빈값 |

---

## 미해결 질문 / Reviewer 검토 포인트

1. **공개 채널 생성 API 없음** — PUBLIC 채널은 현재 시드/마이그레이션으로만 생성 가능. FE-wire-7에서 필요하면 BE에 POST `/api/chat/channels` 추가 요청.
2. **읽음 처리** — `ChatMember.lastReadMessageId` 필드 있으나 업데이트 API 미구현 (스펙 외). 필요 시 PATCH 추가.
3. **메시지 TEXT vs VARCHAR** — DB는 TEXT, DTO `@Size(max=2000)` 검증. 변경 시 DTO만 수정.
