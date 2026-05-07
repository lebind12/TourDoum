<script setup lang="ts">
/**
 * ReservationListCard — 마이페이지 내 예약 목록 항목
 *
 * Props:
 *   reservationId:     예약 번호
 *   accommodationName: 숙소명
 *   checkIn:           체크인 날짜 (YYYY-MM-DD)
 *   checkOut:          체크아웃 날짜 (YYYY-MM-DD)
 *   status:            'confirmed' | 'cancelled' | 'completed'
 *   totalPrice:        결제 금액 (원)
 *
 * Emits:
 *   cancel — 취소 버튼 클릭 시 (reservationId 전달)
 *
 * 사용 예:
 *   <ReservationListCard
 *     reservation-id="TD-20260507-001"
 *     accommodation-name="제주 바다 게스트하우스"
 *     check-in="2026-05-10"
 *     check-out="2026-05-12"
 *     status="confirmed"
 *     :total-price="320000"
 *     @cancel="handleCancel"
 *   />
 */
type ReservationStatus = "confirmed" | "cancelled" | "completed";

interface Props {
	reservationId: string;
	accommodationName: string;
	checkIn: string;
	checkOut: string;
	status: ReservationStatus;
	totalPrice: number;
}

const props = defineProps<Props>();
const emit = defineEmits<(e: "cancel", reservationId: string) => void>();

function formatKRW(amount: number): string {
	return `${amount.toLocaleString("ko-KR")}원`;
}

function formatDate(dateStr: string): string {
	const d = new Date(dateStr);
	return d.toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
}

const statusLabel: Record<ReservationStatus, string> = {
	confirmed: "예약 확정",
	cancelled: "취소됨",
	completed: "이용 완료",
};

const statusClass: Record<ReservationStatus, string> = {
	confirmed: "bg-primary/10 text-primary",
	cancelled: "bg-destructive/10 text-destructive",
	completed: "bg-muted text-muted-foreground",
};
</script>

<template>
  <article
    class="rounded-lg border bg-card text-card-foreground p-4 space-y-3"
    :aria-label="`예약 ${props.reservationId}`"
  >
    <!-- 상단: 숙소명 + 상태 배지 -->
    <div class="flex items-start justify-between gap-2">
      <h4 class="text-sm font-semibold leading-snug">{{ props.accommodationName }}</h4>
      <span
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium flex-shrink-0"
        :class="statusClass[props.status]"
      >
        {{ statusLabel[props.status] }}
      </span>
    </div>

    <!-- 날짜·예약번호 -->
    <div class="text-xs text-muted-foreground space-y-0.5">
      <p>
        <span class="font-medium text-foreground">{{ formatDate(props.checkIn) }}</span>
        <span aria-hidden="true"> — </span>
        <span class="font-medium text-foreground">{{ formatDate(props.checkOut) }}</span>
      </p>
      <p class="font-mono">{{ props.reservationId }}</p>
    </div>

    <!-- 하단: 가격 + 취소 버튼 -->
    <div class="flex items-center justify-between pt-1 border-t">
      <span class="text-sm font-semibold">{{ formatKRW(props.totalPrice) }}</span>

      <button
        v-if="props.status === 'confirmed'"
        type="button"
        class="text-xs text-destructive hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded"
        :aria-label="`${props.accommodationName} 예약 취소`"
        @click="emit('cancel', props.reservationId)"
      >
        예약 취소
      </button>
    </div>
  </article>
</template>
