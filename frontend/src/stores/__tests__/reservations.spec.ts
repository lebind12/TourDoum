import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  RESERVATIONS_STORAGE_KEY,
  useReservationsStore,
  validateReservationDates,
} from '../reservations'

describe('useReservationsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.useRealTimers()
  })

  it('start → setDates → setGuests → setPaymentMethod → confirm 흐름으로 예약을 확정한다', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-05-07T12:00:00.000Z'))
    const store = useReservationsStore()

    store.start(1)
    store.setDates({ checkIn: '2026-05-10', checkOut: '2026-05-12' })
    store.setGuests({ adults: 2, children: 1 })
    store.setPaymentMethod('easy')

    const reservation = store.confirm()

    expect(reservation.id).toMatch(/^R-/)
    expect(store.confirmed).toHaveLength(1)
    expect(store.confirmed[0]).toMatchObject({
      accommodationId: 1,
      checkIn: '2026-05-10',
      checkOut: '2026-05-12',
      adults: 2,
      children: 1,
      paymentMethod: 'easy',
      status: 'confirmed',
    })
    expect(store.current).toBeNull()
  })

  it('confirm 시 confirmed 예약을 localStorage에 저장한다', () => {
    const store = useReservationsStore()

    store.start(2)
    store.setDates({ checkIn: '2026-06-01', checkOut: '2026-06-02' })
    store.setGuests({ adults: 1, children: 0 })
    store.setPaymentMethod('card')
    store.confirm()

    const persisted = JSON.parse(localStorage.getItem(RESERVATIONS_STORAGE_KEY) ?? '[]')
    expect(persisted).toHaveLength(1)
    expect(persisted[0].accommodationId).toBe(2)
  })

  it('hydrate로 localStorage의 confirmed 예약을 복원한다', () => {
    const original = useReservationsStore()
    original.start(3)
    original.setDates({ checkIn: '2026-07-10', checkOut: '2026-07-13' })
    original.setGuests({ adults: 2, children: 0 })
    original.setPaymentMethod('bank')
    const reservation = original.confirm()

    setActivePinia(createPinia())
    const restored = useReservationsStore()
    restored.hydrate()

    expect(restored.confirmed).toHaveLength(1)
    expect(restored.confirmed[0].id).toBe(reservation.id)
    expect(restored.confirmed[0].paymentMethod).toBe('bank')
  })

  it('cancel은 예약 상태를 canceled로 바꾸고 저장한다', () => {
    const store = useReservationsStore()
    store.start(4)
    store.setDates({ checkIn: '2026-08-01', checkOut: '2026-08-03' })
    store.setGuests({ adults: 1, children: 1 })
    store.setPaymentMethod('card')
    const reservation = store.confirm()

    store.cancel(reservation.id)

    expect(store.confirmed[0].status).toBe('canceled')
    const persisted = JSON.parse(localStorage.getItem(RESERVATIONS_STORAGE_KEY) ?? '[]')
    expect(persisted[0].status).toBe('canceled')
  })

  it('체크아웃이 체크인보다 빠르면 날짜 검증에 실패한다', () => {
    const store = useReservationsStore()

    store.start(5)

    expect(validateReservationDates('2026-09-10', '2026-09-09')).toContain('체크아웃')
    expect(() => store.setDates({ checkIn: '2026-09-10', checkOut: '2026-09-09' })).toThrow(
      '체크아웃',
    )
  })
})
