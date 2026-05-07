# handoff — be/plans-domain

## 완료 내용

- `POST /api/plans` — 여행 계획 생성 (201)
- `GET /api/plans/me` — 내 여행 계획 목록 (아이템 없는 요약, 최신순)
- `GET /api/plans/{id}` — 여행 계획 상세 (아이템 포함, 403 권한 체크)
- `POST /api/plans/{id}/items` — 아이템 추가 (201)
- `PATCH /api/plans/{id}/items/reorder` — drag reorder (dayIndex + orderIndex 일괄 갱신)
- `DELETE /api/plans/{id}` — 계획 삭제 (cascade → 아이템 포함, 204)
- Flyway V11: plans + plan_items (FK ON DELETE CASCADE)
- GlobalExceptionHandler: PlanNotFound(404), PlanForbidden(403) 추가
- Mockito 단위테스트 2종 (reorder 정렬, 날짜 유효성 검증)

---

## FE 연결 가이드 (Task #25 — FE-wire-5)

### 1. FE store `plans.ts` 필드 매핑

| FE store 필드 | BE 응답 필드 | 변환 |
|---|---|---|
| `id` (string) | `id` (Long) | `res.id.toString()` |
| `title` | `title` | 동일 |
| `startDate` / `endDate` | `startDate` / `endDate` | 'YYYY-MM-DD' 동일 |
| `items[].type` (소문자) | `targetType` (대문자) | `.toLowerCase()` |
| `items[].itemId` | `targetId` | 동일 |
| `items[].dayIndex` | `dayIndex` | 동일 |
| `items[].order` | `orderIndex` | 필드명 다름: `orderIndex` |
| `items[].note` | `memo` | 필드명 다름: `memo` |

### 2. 계획 생성

```typescript
// POST /api/plans
const plan = await fetch('/api/plans', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    title: state.title,
    startDate: state.startDate,  // 'YYYY-MM-DD'
    endDate: state.endDate,
  }),
}).then(r => r.json())
```

### 3. 아이템 추가

```typescript
// POST /api/plans/{id}/items
await fetch(`/api/plans/${planId}/items`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    dayIndex: item.dayIndex,
    orderIndex: item.order,        // FE 'order' → BE 'orderIndex'
    targetType: item.type.toUpperCase(),  // 'attraction' → 'ATTRACTION'
    targetId: item.itemId,
    memo: item.note ?? null,       // FE 'note' → BE 'memo'
  }),
})
```

### 4. Drag Reorder

```typescript
// PATCH /api/plans/{id}/items/reorder
// FE drag 완료 후 전체 아이템 순서 배열을 전송
await fetch(`/api/plans/${planId}/items/reorder`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    items: plan.items.map(item => ({
      id: Number(item.id),       // string → number
      dayIndex: item.dayIndex,
      orderIndex: item.order,    // FE 'order' → BE 'orderIndex'
    })),
  }),
})
```

### 5. 상세 조회 (아이템 포함)

```typescript
// GET /api/plans/{id}
const detail = await fetch(`/api/plans/${planId}`).then(r => r.json())
// items 배열: [{ id, planId, dayIndex, orderIndex, targetType, targetId, memo }]

// FE store 변환
const storeItems = detail.items.map(i => ({
  id: i.id.toString(),
  dayIndex: i.dayIndex,
  order: i.orderIndex,          // BE 'orderIndex' → FE 'order'
  type: i.targetType.toLowerCase(),  // 'ATTRACTION' → 'attraction'
  itemId: i.targetId,
  note: i.memo,
}))
```

### 6. Pinia store 교체 패턴

```typescript
// src/stores/plans.ts
actions: {
  async createPlan(payload) {
    const res = await $fetch('/api/plans', { method: 'POST', body: payload })
    this.plans.unshift(this.toStorePlan(res))
  },
  async fetchMyPlans() {
    const list = await $fetch('/api/plans/me')
    this.plans = list.map(this.toStorePlan)
  },
  async fetchDetail(id: string) {
    const res = await $fetch(`/api/plans/${id}`)
    this.current = this.toStorePlanWithItems(res)
  },
  async addItem(planId: string, item) {
    await $fetch(`/api/plans/${planId}/items`, {
      method: 'POST',
      body: { ...item, targetType: item.type.toUpperCase(), targetId: item.itemId,
              memo: item.note, orderIndex: item.order },
    })
    await this.fetchDetail(planId)  // 재조회
  },
  async reorder(planId: string, items) {
    await $fetch(`/api/plans/${planId}/items/reorder`, {
      method: 'PATCH',
      body: { items: items.map(i => ({
        id: Number(i.id), dayIndex: i.dayIndex, orderIndex: i.order })) },
    })
  },
  async deletePlan(id: string) {
    await $fetch(`/api/plans/${id}`, { method: 'DELETE' })
    this.plans = this.plans.filter(p => p.id !== id)
  },
}
```

---

## 미해결 질문 / Reviewer 검토 포인트

1. **상세 조회 권한**: `GET /api/plans/{id}` — 현재 본인 계획만 조회 가능. 공유 계획 기능 필요 시 정책 변경 필요.
2. **아이템 삭제 엔드포인트 없음**: 스펙에 없어서 미구현. FE가 필요할 경우 `DELETE /api/plans/{planId}/items/{itemId}` 추가 요청.
3. **날짜 범위 검증**: `startDate == endDate` (당일치기)는 현재 거부됨 — 허용 여부 확인 필요.
4. **목록 vs 상세 분리**: `myList`는 아이템 없는 요약, `detail`은 아이템 포함. N+1 없음 (`@OneToMany`는 EAGER 아님 — `detail` 호출 시 lazy loading).
