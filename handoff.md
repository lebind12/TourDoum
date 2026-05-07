# handoff — be/reservations-domain

## 완료 내용

- `POST /api/reservations/quote` — 가격 견적 (DB 저장 없음, SESSION 인증 필수)
- `POST /api/reservations` — 예약 확정 (Idempotency-Key 헤더 필수, 201 반환)
- `GET /api/reservations/me` — 내 예약 목록 (최신순)
- `POST /api/reservations/{id}/cancel` — 예약 취소 (본인 예약만 가능)
- Flyway V10: `reservations` 테이블 + `uk_reservations_idempotency_key` UNIQUE 인덱스
- `GlobalExceptionHandler` — ReservationNotFound(404), ReservationForbidden(403), IllegalArgument(400) 추가

---

## FE 연결 가이드 (Task #23 — FE-wire-4)

### 1. PaymentMethod 매핑

FE `reservations.ts` mock의 `paymentMethod`는 소문자(`'card'`, `'kakaopay'`, `'toss'`)를 사용하나
BE는 대문자 enum을 받는다.

| FE mock값 | BE PaymentMethod |
|---|---|
| `'card'` | `'CARD'` |
| `'kakaopay'` | `'KAKAOPAY'` |
| `'toss'` | `'TOSS'` |

```typescript
// FE 요청 시 대문자 변환
paymentMethod: store.selectedPayment.toUpperCase()
```

### 2. 인원 수 매핑

FE `ReservationDatesView`의 `adults` + `children` → BE `guests` (합산):

```typescript
guests: state.adults + state.children
```

### 3. 3단계 예약 플로우

#### Step 1 — 날짜/인원 선택 후 견적 요청

```typescript
// POST /api/reservations/quote
const quote = await fetch('/api/reservations/quote', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    accommodationId: accommodationId,
    checkIn: checkIn,       // 'YYYY-MM-DD'
    checkOut: checkOut,     // 'YYYY-MM-DD'
    guests: adults + children,
  }),
}).then(r => r.json())

// quote 응답 필드:
// { accommodationId, checkIn, checkOut, nights, guests,
//   pricePerNight, cleaningFee, totalPrice }
```

#### Step 2 — 결제 수단 선택 후 확정

```typescript
// POST /api/reservations (Idempotency-Key 헤더 필수)
const idempotencyKey = crypto.randomUUID()

const reservation = await fetch('/api/reservations', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Idempotency-Key': idempotencyKey,   // 중복 방지 UUID
  },
  body: JSON.stringify({
    accommodationId: accommodationId,
    checkIn: checkIn,
    checkOut: checkOut,
    guests: adults + children,
    paymentMethod: selectedPayment.toUpperCase(),  // 'CARD' | 'KAKAOPAY' | 'TOSS'
  }),
}).then(r => r.json())
```

**멱등성**: 네트워크 재시도 시 동일한 `idempotencyKey`로 재요청하면 기존 예약을 그대로 반환한다.
`idempotencyKey`를 세션스토리지에 저장해 결제 완료 뷰로 넘기면 안전하다.

#### Step 3 — 내 예약 목록 조회

```typescript
// GET /api/reservations/me
const myReservations = await fetch('/api/reservations/me').then(r => r.json())

// 응답 배열 요소:
// { id, memberId, accommodationId, checkIn, checkOut, guests,
//   totalPrice, paymentMethod, status, idempotencyKey, createdAt }
```

#### 취소

```typescript
// POST /api/reservations/{id}/cancel
await fetch(`/api/reservations/${reservationId}/cancel`, { method: 'POST' })
```

### 4. Pinia store 교체 패턴

```typescript
// src/stores/reservations.ts
import { defineStore } from 'pinia'

export const useReservationStore = defineStore('reservation', {
  state: () => ({
    quote: null as ReservationQuoteResponse | null,
    current: null as ReservationResponse | null,
    list: [] as ReservationResponse[],
    idempotencyKey: '',
  }),
  actions: {
    async fetchQuote(payload: QuotePayload) {
      this.idempotencyKey = crypto.randomUUID()  // 견적 단계에서 미리 생성
      this.quote = await $fetch('/api/reservations/quote', {
        method: 'POST', body: payload,
      })
    },
    async confirm(payload: ConfirmPayload) {
      this.current = await $fetch('/api/reservations', {
        method: 'POST',
        headers: { 'Idempotency-Key': this.idempotencyKey },
        body: { ...payload, paymentMethod: payload.paymentMethod.toUpperCase() },
      })
    },
    async fetchMyList() {
      this.list = await $fetch('/api/reservations/me')
    },
    async cancel(id: number) {
      await $fetch(`/api/reservations/${id}/cancel`, { method: 'POST' })
      this.list = this.list.filter(r => r.id !== id)
    },
  },
})
```

### 5. CLEANING_FEE 동기화

BE `ReservationService.CLEANING_FEE = 20_000` (원).
FE에 동일한 상수가 있다면 삭제하고 quote 응답의 `cleaningFee` 필드를 사용할 것.

---

## 미해결 질문 / Reviewer 검토 포인트

1. **상태코드**: `confirm` 멱등 재요청 시 현재 항상 201을 반환한다 (컨트롤러가 status 분기 없음). 재요청 시 200을 돌려줘야 하는지 FE 팀과 협의 필요.
2. **guests 검증**: 현재 `@Min(1)` 검증이 없음 — `ReservationConfirmRequest`에 추가 고려.
3. **날짜 범위 검증**: `checkIn >= today` 검증 없음 — 비즈니스 룰 확인 후 추가.
4. **예약 완료 뷰 네비게이션**: `ReservationCompleteView`에서 `idempotencyKey`를 어떻게 전달할지 FE 설계 필요.
