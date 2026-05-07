import { useChatStore } from '@/stores/chat'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

describe('ChatStore (mockup)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('공개 채널 3개가 있다', () => {
    const store = useChatStore()
    expect(store.channels).toHaveLength(3)
  })

  it('각 채널에 메시지가 있다', () => {
    const store = useChatStore()
    for (const ch of store.channels) {
      expect(store.messages[ch.id].length).toBeGreaterThan(0)
    }
  })

  it('setActiveChannel 이 activeChannel 을 변경한다', () => {
    const store = useChatStore()
    store.setActiveChannel(1)
    expect(store.activeChannel?.name).toBe('제주여행')
  })

  it('sendMessage 가 메시지를 추가한다', async () => {
    const store = useChatStore()
    store.setActiveChannel(1)
    const before = store.activeMessages.length
    await store.sendMessage(1, '테스트 메시지')
    expect(store.activeMessages.length).toBe(before + 1)
    expect(store.activeMessages.at(-1)?.content).toBe('테스트 메시지')
  })

  it('DM 스레드가 1개 있다', () => {
    const store = useChatStore()
    expect(Object.keys(store.dmThreads)).toHaveLength(1)
  })

  it('setActiveDm 이 activeDmThread 를 변경한다', () => {
    const store = useChatStore()
    store.setActiveDm(101)
    expect(store.activeDmThread?.partnerName).toBe('김민준')
  })

  it('sendDm 이 메시지를 추가한다', async () => {
    const store = useChatStore()
    store.setActiveDm(101)
    const before = store.activeDmThread?.messages.length ?? 0
    await store.sendDm(101, '숙소 문의드려요')
    expect(store.activeDmThread?.messages.length).toBe(before + 1)
  })
})
