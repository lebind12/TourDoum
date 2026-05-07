import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export interface ChatMessage {
  id: number
  channelId: number
  authorId: number
  authorName: string
  content: string
  createdAt: string
}

export interface ChatChannel {
  id: number
  name: string
  description: string
  memberCount: number
  lastMessage: string
  lastMessageAt: string
  isPublic: true
}

export interface DmMessage {
  id: number
  senderId: number
  receiverId: number
  content: string
  createdAt: string
}

export interface DmThread {
  partnerId: number
  partnerName: string
  partnerRole: 'HOST' | 'GUEST'
  accommodationName: string
  messages: DmMessage[]
}

const SEED_CHANNELS: ChatChannel[] = [
  {
    id: 1,
    name: '제주여행',
    description: '제주도 여행 정보와 팁을 공유하는 채널',
    memberCount: 1284,
    lastMessage: '이번 주말 성산일출봉 날씨 어때요?',
    lastMessageAt: '2026-05-07T14:22:00Z',
    isPublic: true,
  },
  {
    id: 2,
    name: '부산맛집',
    description: '부산의 숨겨진 맛집을 공유하는 채널',
    memberCount: 987,
    lastMessage: '남포동 돼지국밥 추천해요!',
    lastMessageAt: '2026-05-07T13:45:00Z',
    isPublic: true,
  },
  {
    id: 3,
    name: '등산',
    description: '전국 산행 정보와 코스를 공유하는 채널',
    memberCount: 756,
    lastMessage: '설악산 공룡능선 내일 같이 갈 분?',
    lastMessageAt: '2026-05-07T12:10:00Z',
    isPublic: true,
  },
]

const SEED_MESSAGES: Record<number, ChatMessage[]> = {
  1: [
    {
      id: 1,
      channelId: 1,
      authorId: 201,
      authorName: '여행러버',
      content: '제주도 성산일출봉 입장료 얼마예요?',
      createdAt: '2026-05-07T08:00:00Z',
    },
    {
      id: 2,
      channelId: 1,
      authorId: 202,
      authorName: '제주아이',
      content: '성인 2,000원이에요! 일출 시간에 맞춰 가면 정말 아름다워요',
      createdAt: '2026-05-07T08:05:00Z',
    },
    {
      id: 3,
      channelId: 1,
      authorId: 203,
      authorName: '하이킹걸',
      content: '오늘 한라산 등반했는데 날씨 진짜 좋았어요 ☀️',
      createdAt: '2026-05-07T09:10:00Z',
    },
    {
      id: 4,
      channelId: 1,
      authorId: 204,
      authorName: '감귤농부',
      content: '제주 감귤 체험 농장 추천 드려요! 서쪽 한림 근처에 있어요',
      createdAt: '2026-05-07T09:30:00Z',
    },
    {
      id: 5,
      channelId: 1,
      authorId: 201,
      authorName: '여행러버',
      content: '협재해수욕장 지금 수영 가능한가요?',
      createdAt: '2026-05-07T10:15:00Z',
    },
    {
      id: 6,
      channelId: 1,
      authorId: 205,
      authorName: '서핑왕',
      content: '아직 수온이 좀 낮아서 웻슈트 필요해요 ㅎㅎ',
      createdAt: '2026-05-07T10:20:00Z',
    },
    {
      id: 7,
      channelId: 1,
      authorId: 202,
      authorName: '제주아이',
      content: '우도 올레길 추천! 자전거 타고 돌면 3-4시간이면 충분해요',
      createdAt: '2026-05-07T11:00:00Z',
    },
    {
      id: 8,
      channelId: 1,
      authorId: 206,
      authorName: '혼여족',
      content: '혼자 제주 3박4일 일정 짜는데 도와주실 분?',
      createdAt: '2026-05-07T11:30:00Z',
    },
    {
      id: 9,
      channelId: 1,
      authorId: 203,
      authorName: '하이킹걸',
      content: '@혼여족 어느 시기에 가세요? 계절마다 추천코스가 달라요!',
      createdAt: '2026-05-07T11:35:00Z',
    },
    {
      id: 10,
      channelId: 1,
      authorId: 206,
      authorName: '혼여족',
      content: '이번 달 말 예정이에요!',
      createdAt: '2026-05-07T11:40:00Z',
    },
    {
      id: 11,
      channelId: 1,
      authorId: 204,
      authorName: '감귤농부',
      content: '5월 말이면 청보리 축제도 있어요 가파도에서! 꼭 들르세요',
      createdAt: '2026-05-07T12:00:00Z',
    },
    {
      id: 12,
      channelId: 1,
      authorId: 207,
      authorName: '맛집탐방러',
      content: '제주 흑돼지 맛집 신화월드 근처에 있는 곳 아는 분?',
      createdAt: '2026-05-07T12:30:00Z',
    },
    {
      id: 13,
      channelId: 1,
      authorId: 202,
      authorName: '제주아이',
      content: "애월 근처 '덕분에'라는 카페 분위기 정말 좋아요 뷰 맛집",
      createdAt: '2026-05-07T13:00:00Z',
    },
    {
      id: 14,
      channelId: 1,
      authorId: 201,
      authorName: '여행러버',
      content: '이번 주말 성산일출봉 날씨 어때요?',
      createdAt: '2026-05-07T14:22:00Z',
    },
  ],
  2: [
    {
      id: 1,
      channelId: 2,
      authorId: 301,
      authorName: '부산토박이',
      content: '자갈치 시장 옆 회센터 추천합니다! 활어 회 진짜 신선해요',
      createdAt: '2026-05-07T07:30:00Z',
    },
    {
      id: 2,
      channelId: 2,
      authorId: 302,
      authorName: '국밥마니아',
      content: "남포동 돼지국밥 골목에서 '할매국밥' 꼭 드셔보세요",
      createdAt: '2026-05-07T08:00:00Z',
    },
    {
      id: 3,
      channelId: 2,
      authorId: 303,
      authorName: '밀면러버',
      content: '부산 밀면도 꼭 드세요! 송도 근처 유명한 집 있어요',
      createdAt: '2026-05-07T08:30:00Z',
    },
    {
      id: 4,
      channelId: 2,
      authorId: 301,
      authorName: '부산토박이',
      content: '광안리 스카이라운지에서 야경 보면서 맥주 한 잔 추천!',
      createdAt: '2026-05-07T09:00:00Z',
    },
    {
      id: 5,
      channelId: 2,
      authorId: 304,
      authorName: '카페홀릭',
      content: '전포카페거리 한번은 꼭 가보세요. 독특한 카페들 많아요',
      createdAt: '2026-05-07T09:45:00Z',
    },
    {
      id: 6,
      channelId: 2,
      authorId: 302,
      authorName: '국밥마니아',
      content: '씨앗호떡! 국제시장 씨앗호떡은 부산 필수 간식이에요',
      createdAt: '2026-05-07T10:30:00Z',
    },
    {
      id: 7,
      channelId: 2,
      authorId: 305,
      authorName: '해산물왕',
      content: '기장 대게 철 지났나요? 아직 드실 수 있나요?',
      createdAt: '2026-05-07T11:00:00Z',
    },
    {
      id: 8,
      channelId: 2,
      authorId: 301,
      authorName: '부산토박이',
      content: '5월은 좀 애매해요. 겨울이 제철인데 요즘도 파는 곳은 있어요',
      createdAt: '2026-05-07T11:10:00Z',
    },
    {
      id: 9,
      channelId: 2,
      authorId: 303,
      authorName: '밀면러버',
      content: '해운대 근처 수제버거 맛집 생겼던데 아직 안 가봤는데 어때요?',
      createdAt: '2026-05-07T12:00:00Z',
    },
    {
      id: 10,
      channelId: 2,
      authorId: 304,
      authorName: '카페홀릭',
      content: '감천문화마을 올라가다 보면 작은 카페들 분위기 좋아요',
      createdAt: '2026-05-07T13:00:00Z',
    },
    {
      id: 11,
      channelId: 2,
      authorId: 302,
      authorName: '국밥마니아',
      content: '남포동 돼지국밥 추천해요!',
      createdAt: '2026-05-07T13:45:00Z',
    },
  ],
  3: [
    {
      id: 1,
      channelId: 3,
      authorId: 401,
      authorName: '산악회장',
      content: '이번 주 한라산 윗세오름 탐방 신청 마감됐나요?',
      createdAt: '2026-05-07T06:00:00Z',
    },
    {
      id: 2,
      channelId: 3,
      authorId: 402,
      authorName: '백두대간',
      content: '국립공원 예약은 국립공원공단 앱에서 하셔야 해요!',
      createdAt: '2026-05-07T06:30:00Z',
    },
    {
      id: 3,
      channelId: 3,
      authorId: 403,
      authorName: '가을단풍',
      content: '설악산 공룡능선 내려올 때 무릎 조심하세요. 스틱 필수!',
      createdAt: '2026-05-07T07:00:00Z',
    },
    {
      id: 4,
      channelId: 3,
      authorId: 404,
      authorName: '새벽봉우리',
      content: '지리산 종주 3박4일 일정 공유해드릴게요. DM 주세요',
      createdAt: '2026-05-07T07:30:00Z',
    },
    {
      id: 5,
      channelId: 3,
      authorId: 401,
      authorName: '산악회장',
      content: '덕유산 향적봉 설경이 최고인데 이미 눈은 다 녹았겠죠?',
      createdAt: '2026-05-07T08:00:00Z',
    },
    {
      id: 6,
      channelId: 3,
      authorId: 402,
      authorName: '백두대간',
      content: '5월엔 철쭉! 황매산 철쭉 지금 딱 절정이에요',
      createdAt: '2026-05-07T08:30:00Z',
    },
    {
      id: 7,
      channelId: 3,
      authorId: 405,
      authorName: '초보등산객',
      content: '등산 초보인데 북한산 코스 추천해주실 분 있나요?',
      createdAt: '2026-05-07T09:00:00Z',
    },
    {
      id: 8,
      channelId: 3,
      authorId: 403,
      authorName: '가을단풍',
      content:
        '@초보등산객 북한산 둘레길부터 시작하세요! 약 71km 총 21코스인데 각 구간이 짧아서 부담 없어요',
      createdAt: '2026-05-07T09:10:00Z',
    },
    {
      id: 9,
      channelId: 3,
      authorId: 404,
      authorName: '새벽봉우리',
      content:
        '주왕산 주산지 새벽 물안개 정말 장관이에요. 새벽 4시에 출발하면 일출 보면서 찍을 수 있어요',
      createdAt: '2026-05-07T10:00:00Z',
    },
    {
      id: 10,
      channelId: 3,
      authorId: 401,
      authorName: '산악회장',
      content: '이번 달 관악산 정기 산행 일자 확정됐어요. 5월 17일 토요일 09:00 사당역 집결!',
      createdAt: '2026-05-07T11:00:00Z',
    },
    {
      id: 11,
      channelId: 3,
      authorId: 406,
      authorName: '트레일러너',
      content: '소백산 연화봉 지금 야생화 장관이에요 꼭 가보세요!',
      createdAt: '2026-05-07T11:30:00Z',
    },
    {
      id: 12,
      channelId: 3,
      authorId: 407,
      authorName: '오름탐방',
      content: '제주 오름 중 가장 추천하는 곳은 어디예요?',
      createdAt: '2026-05-07T12:00:00Z',
    },
    {
      id: 13,
      channelId: 3,
      authorId: 402,
      authorName: '백두대간',
      content: '다랑쉬오름이 뷰가 최고예요. 일몰 시간에 맞춰 가세요',
      createdAt: '2026-05-07T12:10:00Z',
    },
    {
      id: 14,
      channelId: 3,
      authorId: 401,
      authorName: '산악회장',
      content: '설악산 공룡능선 내일 같이 갈 분?',
      createdAt: '2026-05-07T12:10:00Z',
    },
  ],
}

/** 숙박 주인 1명과의 DM (숙박 문의) */
const SEED_DM: Record<number, DmThread> = {
  101: {
    partnerId: 101,
    partnerName: '김민준',
    partnerRole: 'HOST',
    accommodationName: '제주 해변 리조트',
    messages: [
      {
        id: 1,
        senderId: 0,
        receiverId: 101,
        content: '안녕하세요! 제주 해변 리조트 5월 31일 2박 가능한가요?',
        createdAt: '2026-05-06T10:00:00Z',
      },
      {
        id: 2,
        senderId: 101,
        receiverId: 0,
        content: '네 안녕하세요! 5월 31일부터 2박은 가능합니다 😊 인원이 몇 분이세요?',
        createdAt: '2026-05-06T10:15:00Z',
      },
      {
        id: 3,
        senderId: 0,
        receiverId: 101,
        content: '2명이요. 오션뷰 객실로 부탁드려요!',
        createdAt: '2026-05-06T10:20:00Z',
      },
      {
        id: 4,
        senderId: 101,
        receiverId: 0,
        content:
          '2인 오션뷰 객실은 1박 28만원이에요. 체크인은 오후 3시, 체크아웃은 오전 11시입니다.',
        createdAt: '2026-05-06T10:30:00Z',
      },
      {
        id: 5,
        senderId: 0,
        receiverId: 101,
        content: '조식 포함인가요?',
        createdAt: '2026-05-06T10:35:00Z',
      },
      {
        id: 6,
        senderId: 101,
        receiverId: 0,
        content: '조식은 별도 1인 2만원입니다. 미리 예약하시면 포함해드릴 수 있어요!',
        createdAt: '2026-05-06T10:40:00Z',
      },
      {
        id: 7,
        senderId: 0,
        receiverId: 101,
        content: '좋아요! 조식 2명 포함해서 예약 진행할게요.',
        createdAt: '2026-05-06T10:45:00Z',
      },
      {
        id: 8,
        senderId: 101,
        receiverId: 0,
        content:
          '예약 확정됐습니다! 총 62만원이고요, 방문 전 궁금한 점 있으시면 언제든 메시지 주세요 🙏',
        createdAt: '2026-05-06T11:00:00Z',
      },
    ],
  },
}

export const useChatStore = defineStore('chat', () => {
  const channels = ref<ChatChannel[]>([...SEED_CHANNELS])
  const messages = ref<Record<number, ChatMessage[]>>({ ...SEED_MESSAGES })
  const dmThreads = ref<Record<number, DmThread>>({ ...SEED_DM })
  const activeChannelId = ref<number | null>(null)
  const activeDmPartnerId = ref<number | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const activeChannel = computed(
    () => channels.value.find((c) => c.id === activeChannelId.value) ?? null,
  )

  const activeMessages = computed<ChatMessage[]>(() =>
    activeChannelId.value !== null ? messages.value[activeChannelId.value] ?? [] : [],
  )

  const activeDmThread = computed<DmThread | null>(() =>
    activeDmPartnerId.value !== null ? dmThreads.value[activeDmPartnerId.value] ?? null : null,
  )

  function setActiveChannel(channelId: number) {
    activeChannelId.value = channelId
    activeDmPartnerId.value = null
  }

  function setActiveDm(partnerId: number) {
    activeDmPartnerId.value = partnerId
    activeChannelId.value = null
  }

  /** BE 연결 시 이 함수만 교체 */
  async function fetchChannels(): Promise<void> {
    loading.value = true
    error.value = null
    await new Promise((r) => setTimeout(r, 80))
    loading.value = false
  }

  /** mockup: 채널에 새 메시지 추가 */
  async function sendMessage(channelId: number, content: string): Promise<void> {
    const newMsg: ChatMessage = {
      id: (messages.value[channelId]?.length ?? 0) + 1,
      channelId,
      authorId: 0, // 현재 사용자 (auth store에서 가져올 예정)
      authorName: '나',
      content,
      createdAt: new Date().toISOString(),
    }
    if (!messages.value[channelId]) {
      messages.value[channelId] = []
    }
    messages.value[channelId].push(newMsg)

    // 채널 lastMessage 갱신
    const ch = channels.value.find((c) => c.id === channelId)
    if (ch) {
      ch.lastMessage = content
      ch.lastMessageAt = newMsg.createdAt
    }
  }

  /** mockup: DM 새 메시지 추가 */
  async function sendDm(partnerId: number, content: string): Promise<void> {
    const thread = dmThreads.value[partnerId]
    if (!thread) return
    const newMsg: DmMessage = {
      id: thread.messages.length + 1,
      senderId: 0,
      receiverId: partnerId,
      content,
      createdAt: new Date().toISOString(),
    }
    thread.messages.push(newMsg)
  }

  return {
    channels,
    messages,
    dmThreads,
    activeChannelId,
    activeDmPartnerId,
    loading,
    error,
    activeChannel,
    activeMessages,
    activeDmThread,
    setActiveChannel,
    setActiveDm,
    fetchChannels,
    sendMessage,
    sendDm,
  }
})
