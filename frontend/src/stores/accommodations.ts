import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export interface Accommodation {
  id: number
  name: string
  type: '호텔' | '펜션' | '게스트하우스' | '리조트' | '모텔' | '한옥'
  address: string
  latitude: number
  longitude: number
  description: string
  imageUrl: string
  pricePerNight: number
  rating: number
  reviewCount: number
  sido: string
  gugun: string
  hostId: number
  hostName: string
  hostPhone: string
  amenities: string[]
  maxGuests: number
  checkInTime: string
  checkOutTime: string
}

const SEED_ACCOMMODATIONS: Accommodation[] = [
  {
    id: 1,
    name: '제주 해변 리조트',
    type: '리조트',
    address: '제주특별자치도 서귀포시 중문관광로 72번길 75',
    latitude: 33.2482,
    longitude: 126.4128,
    description:
      '제주 중문 해변에 위치한 고급 리조트. 오션뷰 객실과 다양한 편의시설을 갖추고 있습니다.',
    imageUrl: 'https://picsum.photos/seed/acc-1/400/300',
    pricePerNight: 280000,
    rating: 4.7,
    reviewCount: 342,
    sido: '제주',
    gugun: '서귀포시',
    hostId: 101,
    hostName: '김민준',
    hostPhone: '010-1234-5678',
    amenities: ['수영장', '스파', '레스토랑', '피트니스', '주차장', '와이파이'],
    maxGuests: 4,
    checkInTime: '15:00',
    checkOutTime: '11:00',
  },
  {
    id: 2,
    name: '북촌 전통 한옥스테이',
    type: '한옥',
    address: '서울특별시 종로구 계동길 52',
    latitude: 37.5822,
    longitude: 126.9851,
    description:
      '100년 전통의 한옥을 개조한 숙소. 고즈넉한 분위기에서 한국 전통문화를 체험할 수 있습니다.',
    imageUrl: 'https://picsum.photos/seed/acc-2/400/300',
    pricePerNight: 150000,
    rating: 4.8,
    reviewCount: 218,
    sido: '서울',
    gugun: '종로구',
    hostId: 102,
    hostName: '이서연',
    hostPhone: '010-2345-6789',
    amenities: ['한식 조식', '마당', '와이파이', '에어컨'],
    maxGuests: 2,
    checkInTime: '16:00',
    checkOutTime: '11:00',
  },
  {
    id: 3,
    name: '해운대 씨뷰 호텔',
    type: '호텔',
    address: '부산광역시 해운대구 해운대해변로 264',
    latitude: 35.1592,
    longitude: 129.1607,
    description:
      '해운대 해수욕장 바로 앞에 위치한 5성급 호텔. 모든 객실에서 바다 전망을 감상할 수 있습니다.',
    imageUrl: 'https://picsum.photos/seed/acc-3/400/300',
    pricePerNight: 350000,
    rating: 4.6,
    reviewCount: 489,
    sido: '부산',
    gugun: '해운대구',
    hostId: 103,
    hostName: '박준혁',
    hostPhone: '010-3456-7890',
    amenities: ['수영장', '레스토랑', '바', '피트니스', '주차장', '와이파이', '조식 포함'],
    maxGuests: 3,
    checkInTime: '14:00',
    checkOutTime: '12:00',
  },
  {
    id: 4,
    name: '강릉 솔향기 펜션',
    type: '펜션',
    address: '강원도 강릉시 강동면 헌화로 1166',
    latitude: 37.7214,
    longitude: 129.0631,
    description:
      '소나무 숲 속에 자리한 아늑한 펜션. 동해 바다를 바라보며 여유로운 시간을 보낼 수 있습니다.',
    imageUrl: 'https://picsum.photos/seed/acc-4/400/300',
    pricePerNight: 120000,
    rating: 4.5,
    reviewCount: 156,
    sido: '강원',
    gugun: '강릉시',
    hostId: 104,
    hostName: '최은지',
    hostPhone: '010-4567-8901',
    amenities: ['바비큐', '주차장', '와이파이', '에어컨', '넷플릭스'],
    maxGuests: 6,
    checkInTime: '15:00',
    checkOutTime: '11:00',
  },
  {
    id: 5,
    name: '전주 한옥마을 게스트하우스',
    type: '게스트하우스',
    address: '전라북도 전주시 완산구 은행로 26',
    latitude: 35.8152,
    longitude: 127.1519,
    description:
      '전주 한옥마을 내 위치한 게스트하우스. 저렴한 가격에 한옥의 정취를 느낄 수 있습니다.',
    imageUrl: 'https://picsum.photos/seed/acc-5/400/300',
    pricePerNight: 45000,
    rating: 4.3,
    reviewCount: 287,
    sido: '전북',
    gugun: '전주시',
    hostId: 105,
    hostName: '정민서',
    hostPhone: '010-5678-9012',
    amenities: ['공용 주방', '와이파이', '로커'],
    maxGuests: 1,
    checkInTime: '16:00',
    checkOutTime: '10:00',
  },
  {
    id: 6,
    name: '경주 황리단길 모텔',
    type: '모텔',
    address: '경상북도 경주시 포석로 1080',
    latitude: 35.8362,
    longitude: 129.2108,
    description: '황리단길 바로 옆에 위치한 모텔. 불국사, 석굴암 등 경주 명소 접근이 편리합니다.',
    imageUrl: 'https://picsum.photos/seed/acc-6/400/300',
    pricePerNight: 65000,
    rating: 4.1,
    reviewCount: 134,
    sido: '경북',
    gugun: '경주시',
    hostId: 106,
    hostName: '강현우',
    hostPhone: '010-6789-0123',
    amenities: ['주차장', '와이파이', '에어컨'],
    maxGuests: 2,
    checkInTime: '15:00',
    checkOutTime: '11:00',
  },
  {
    id: 7,
    name: '순천만 에코 리조트',
    type: '리조트',
    address: '전라남도 순천시 대대동 162',
    latitude: 34.9088,
    longitude: 127.5006,
    description:
      '순천만 습지 인근에 위치한 에코 리조트. 자연과 하나 되는 특별한 경험을 제공합니다.',
    imageUrl: 'https://picsum.photos/seed/acc-7/400/300',
    pricePerNight: 180000,
    rating: 4.6,
    reviewCount: 203,
    sido: '전남',
    gugun: '순천시',
    hostId: 107,
    hostName: '윤지현',
    hostPhone: '010-7890-1234',
    amenities: ['야외 수영장', '레스토랑', '자전거 대여', '주차장', '와이파이'],
    maxGuests: 4,
    checkInTime: '15:00',
    checkOutTime: '12:00',
  },
  {
    id: 8,
    name: '설악산 산장 펜션',
    type: '펜션',
    address: '강원도 속초시 설악산로 1200',
    latitude: 38.1287,
    longitude: 128.4583,
    description: '설악산 입구 인근의 아늑한 펜션. 등산 후 피로를 풀기에 최적의 장소입니다.',
    imageUrl: 'https://picsum.photos/seed/acc-8/400/300',
    pricePerNight: 95000,
    rating: 4.4,
    reviewCount: 178,
    sido: '강원',
    gugun: '속초시',
    hostId: 108,
    hostName: '조성민',
    hostPhone: '010-8901-2345',
    amenities: ['바비큐', '주차장', '와이파이', '족욕탕'],
    maxGuests: 8,
    checkInTime: '14:00',
    checkOutTime: '11:00',
  },
]

export const useAccommodationsStore = defineStore('accommodations', () => {
  const items = ref<Accommodation[]>([...SEED_ACCOMMODATIONS])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const searchQuery = ref('')
  const selectedType = ref('')
  const selectedSido = ref('')
  const maxPrice = ref<number | null>(null)

  const types = computed<Accommodation['type'][]>(
    () => [...new Set(SEED_ACCOMMODATIONS.map((a) => a.type))] as Accommodation['type'][],
  )

  const sidos = computed(() => [...new Set(SEED_ACCOMMODATIONS.map((a) => a.sido))])

  const filtered = computed(() => {
    return items.value.filter((a) => {
      const matchesSearch =
        !searchQuery.value ||
        a.name.includes(searchQuery.value) ||
        a.address.includes(searchQuery.value)
      const matchesType = !selectedType.value || a.type === selectedType.value
      const matchesSido = !selectedSido.value || a.sido === selectedSido.value
      const matchesPrice = maxPrice.value === null || a.pricePerNight <= maxPrice.value
      return matchesSearch && matchesType && matchesSido && matchesPrice
    })
  })

  function getById(id: number): Accommodation | undefined {
    return items.value.find((a) => a.id === id)
  }

  /** BE 연결 시 이 함수만 교체 */
  async function fetchAccommodations(): Promise<void> {
    loading.value = true
    error.value = null
    await new Promise((r) => setTimeout(r, 100))
    loading.value = false
  }

  function setSearch(query: string) {
    searchQuery.value = query
  }
  function setType(type: string) {
    selectedType.value = type
  }
  function setSido(sido: string) {
    selectedSido.value = sido
  }
  function setMaxPrice(price: number | null) {
    maxPrice.value = price
  }

  return {
    items,
    loading,
    error,
    searchQuery,
    selectedType,
    selectedSido,
    maxPrice,
    types,
    sidos,
    filtered,
    getById,
    fetchAccommodations,
    setSearch,
    setType,
    setSido,
    setMaxPrice,
  }
})
