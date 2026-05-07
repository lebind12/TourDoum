<script setup lang="ts">
/**
 * ReceiptCard — 예약 완료 영수증 카드
 *
 * Props:
 *   reservationId:    예약 번호
 *   accommodationName: 숙소명
 *   checkIn:          체크인 날짜 (YYYY-MM-DD)
 *   checkOut:         체크아웃 날짜 (YYYY-MM-DD)
 *   guests:           총 인원 수
 *   totalPrice:       결제 금액 (원)
 *   paymentMethod:    결제 수단 레이블
 *
 * 사용 예:
 *   <ReceiptCard
 *     reservation-id="TD-20260507-001"
 *     accommodation-name="제주 바다 게스트하우스"
 *     check-in="2026-05-10"
 *     check-out="2026-05-12"
 *     :guests="2"
 *     :total-price="320000"
 *     payment-method="신용/체크카드"
 *   />
 */
interface Props {
	reservationId: string;
	accommodationName: string;
	checkIn: string;
	checkOut: string;
	guests: number;
	totalPrice: number;
	paymentMethod: string;
}

const props = defineProps<Props>();

function formatKRW(amount: number): string {
	return `${amount.toLocaleString("ko-KR")}원`;
}

function formatDate(dateStr: string): string {
	const d = new Date(dateStr);
	return d.toLocaleDateString("ko-KR", {
		year: "numeric",
		month: "long",
		day: "numeric",
	});
}
</script>

<template>
  <article
    class="rounded-xl border bg-card text-card-foreground shadow-sm overflow-hidden"
    aria-label="예약 영수증"
  >
    <!-- 헤더 -->
    <div class="bg-primary px-6 py-5 text-primary-foreground">
      <div class="flex items-center gap-3">
        <span class="text-3xl" aria-hidden="true">✅</span>
        <div>
          <p class="text-sm opacity-80">예약이 확정되었습니다</p>
          <p class="font-bold text-lg">{{ props.accommodationName }}</p>
        </div>
      </div>
    </div>

    <!-- 상세 정보 -->
    <dl class="px-6 py-4 space-y-0 divide-y divide-border text-sm">
      <div class="flex justify-between py-3">
        <dt class="text-muted-foreground font-medium">예약 번호</dt>
        <dd class="font-mono font-semibold text-xs">{{ props.reservationId }}</dd>
      </div>

      <div class="flex justify-between py-3">
        <dt class="text-muted-foreground font-medium">체크인</dt>
        <dd>{{ formatDate(props.checkIn) }}</dd>
      </div>

      <div class="flex justify-between py-3">
        <dt class="text-muted-foreground font-medium">체크아웃</dt>
        <dd>{{ formatDate(props.checkOut) }}</dd>
      </div>

      <div class="flex justify-between py-3">
        <dt class="text-muted-foreground font-medium">인원</dt>
        <dd>{{ props.guests }}명</dd>
      </div>

      <div class="flex justify-between py-3">
        <dt class="text-muted-foreground font-medium">결제 수단</dt>
        <dd>{{ props.paymentMethod }}</dd>
      </div>

      <div class="flex justify-between py-3 font-semibold">
        <dt>결제 금액</dt>
        <dd class="text-primary text-base">{{ formatKRW(props.totalPrice) }}</dd>
      </div>
    </dl>
  </article>
</template>
