import { defineStore } from 'pinia'

export type PaymentMethod = 'card' | 'bank' | 'easy'

export interface Reservation {
  id: string
  accommodationId: number
  checkIn: string
  checkOut: string
  adults: number
  children: number
  paymentMethod: PaymentMethod
  status: 'confirmed' | 'canceled'
  createdAt: string
}

interface State {
  current: Partial<Reservation> | null
  confirmed: Reservation[]
}

// gitleaks가 entropy 기반으로 false positive 처리할 수 있어 storage key는 의도적으로 단순 형태.
// gitleaks:allow
export const RESERVATIONS_STORAGE_KEY = 'tourdoum-reservations-v1'
export const CLEANING_FEE = 20000

const MS_PER_DAY = 24 * 60 * 60 * 1000
const YMD_RE = /^\d{4}-\d{2}-\d{2}$/

function parseYmd(date: string): number | null {
  if (!YMD_RE.test(date)) return null

  const [year, month, day] = date.split('-').map(Number)
  const utc = Date.UTC(year, month - 1, day)
  const parsed = new Date(utc)

  if (
    parsed.getUTCFullYear() !== year ||
    parsed.getUTCMonth() !== month - 1 ||
    parsed.getUTCDate() !== day
  ) {
    return null
  }

  return utc
}

export function validateReservationDates(checkIn: string, checkOut: string): string | null {
  const checkInTime = parseYmd(checkIn)
  const checkOutTime = parseYmd(checkOut)

  if (checkInTime === null || checkOutTime === null) {
    return '체크인과 체크아웃 날짜를 모두 선택해 주세요.'
  }

  if (checkOutTime <= checkInTime) {
    return '체크아웃은 체크인보다 늦어야 합니다.'
  }

  return null
}

export function calculateNights(checkIn: string, checkOut: string): number {
  const error = validateReservationDates(checkIn, checkOut)
  if (error) throw new Error(error)

  const checkInTime = parseYmd(checkIn)
  const checkOutTime = parseYmd(checkOut)
  if (checkInTime === null || checkOutTime === null) throw new Error('잘못된 날짜입니다.')

  return Math.round((checkOutTime - checkInTime) / MS_PER_DAY)
}

export function calculateReservationTotal(
  pricePerNight: number,
  checkIn: string,
  checkOut: string,
  cleaningFee = CLEANING_FEE,
): number {
  return pricePerNight * calculateNights(checkIn, checkOut) + cleaningFee
}

function isPaymentMethod(value: unknown): value is PaymentMethod {
  return value === 'card' || value === 'bank' || value === 'easy'
}

function isReservation(value: unknown): value is Reservation {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Partial<Reservation>

  return (
    typeof candidate.id === 'string' &&
    typeof candidate.accommodationId === 'number' &&
    typeof candidate.checkIn === 'string' &&
    typeof candidate.checkOut === 'string' &&
    typeof candidate.adults === 'number' &&
    typeof candidate.children === 'number' &&
    isPaymentMethod(candidate.paymentMethod) &&
    (candidate.status === 'confirmed' || candidate.status === 'canceled') &&
    typeof candidate.createdAt === 'string' &&
    validateReservationDates(candidate.checkIn, candidate.checkOut) === null &&
    candidate.adults >= 1 &&
    candidate.children >= 0
  )
}

function persistConfirmed(confirmed: Reservation[]) {
  localStorage.setItem(RESERVATIONS_STORAGE_KEY, JSON.stringify(confirmed))
}

export const useReservationsStore = defineStore('reservations', {
  state: (): State => ({
    current: null,
    confirmed: [],
  }),
  actions: {
    start(accommodationId: number) {
      if (!Number.isFinite(accommodationId)) {
        throw new Error('유효한 숙소 ID가 필요합니다.')
      }

      this.current = { accommodationId }
    },
    setDates(payload: { checkIn: string; checkOut: string }) {
      if (!this.current) throw new Error('예약을 먼저 시작해 주세요.')

      const error = validateReservationDates(payload.checkIn, payload.checkOut)
      if (error) throw new Error(error)

      this.current = {
        ...this.current,
        checkIn: payload.checkIn,
        checkOut: payload.checkOut,
      }
    },
    setGuests(payload: { adults: number; children: number }) {
      if (!this.current) throw new Error('예약을 먼저 시작해 주세요.')

      if (
        !Number.isInteger(payload.adults) ||
        !Number.isInteger(payload.children) ||
        payload.adults < 1 ||
        payload.children < 0
      ) {
        throw new Error('인원은 성인 1명 이상으로 선택해 주세요.')
      }

      this.current = {
        ...this.current,
        adults: payload.adults,
        children: payload.children,
      }
    },
    setPaymentMethod(method: PaymentMethod) {
      if (!this.current) throw new Error('예약을 먼저 시작해 주세요.')
      if (!isPaymentMethod(method)) throw new Error('지원하지 않는 결제 수단입니다.')

      this.current = {
        ...this.current,
        paymentMethod: method,
      }
    },
    confirm(): Reservation {
      if (
        !this.current ||
        typeof this.current.accommodationId !== 'number' ||
        typeof this.current.checkIn !== 'string' ||
        typeof this.current.checkOut !== 'string' ||
        typeof this.current.adults !== 'number' ||
        typeof this.current.children !== 'number' ||
        !isPaymentMethod(this.current.paymentMethod)
      ) {
        throw new Error('예약 정보가 완성되지 않았습니다.')
      }

      const dateError = validateReservationDates(this.current.checkIn, this.current.checkOut)
      if (dateError) throw new Error(dateError)

      const reservation: Reservation = {
        id: `R-${Date.now().toString(36)}`,
        accommodationId: this.current.accommodationId,
        checkIn: this.current.checkIn,
        checkOut: this.current.checkOut,
        adults: this.current.adults,
        children: this.current.children,
        paymentMethod: this.current.paymentMethod,
        status: 'confirmed',
        createdAt: new Date().toISOString(),
      }

      this.confirmed.push(reservation)
      this.current = null
      persistConfirmed(this.confirmed)

      return reservation
    },
    cancel(reservationId: string) {
      const reservation = this.confirmed.find((r) => r.id === reservationId)
      if (!reservation) throw new Error('예약을 찾을 수 없습니다.')

      reservation.status = 'canceled'
      persistConfirmed(this.confirmed)
    },
    hydrate() {
      const raw = localStorage.getItem(RESERVATIONS_STORAGE_KEY)
      if (!raw) return

      const parsed: unknown = JSON.parse(raw)
      if (!Array.isArray(parsed)) {
        throw new Error('저장된 예약 데이터 형식이 올바르지 않습니다.')
      }

      this.confirmed = parsed.filter(isReservation)
    },
  },
})
