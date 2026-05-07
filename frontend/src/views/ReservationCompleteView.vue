<script setup lang="ts">
import ReceiptCard from "@/components/reservation/ReceiptCard.vue";
/**
 * ReservationCompleteView — Step 3: 예약 완료
 *
 * 라우트: /reservations/new/:accommodationId/complete
 * TODO(fe): reservations store에서 완료된 예약 정보 읽기
 */
import ReservationStepper from "@/components/reservation/ReservationStepper.vue";
import { Button } from "@/components/ui/button";

// TODO(fe): reservations store에서 완료 예약 정보 읽기 — placeholder
const reservation = {
	reservationId: "TD-20260507-001",
	accommodationName: "제주 바다 게스트하우스",
	checkIn: "2026-05-10",
	checkOut: "2026-05-12",
	guests: 2,
	totalPrice: 320000,
	paymentMethod: "신용/체크카드",
};
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6 pb-8">
    <!-- 단계 인디케이터 -->
    <ReservationStepper :current-step="3" />

    <!-- 완료 메시지 -->
    <div class="text-center space-y-2 py-2">
      <h1 class="text-2xl font-semibold tracking-tight">예약이 완료되었습니다!</h1>
      <p class="text-sm text-muted-foreground">아래 영수증을 확인해 주세요.</p>
    </div>

    <!-- 영수증 카드 -->
    <ReceiptCard
      :reservation-id="reservation.reservationId"
      :accommodation-name="reservation.accommodationName"
      :check-in="reservation.checkIn"
      :check-out="reservation.checkOut"
      :guests="reservation.guests"
      :total-price="reservation.totalPrice"
      :payment-method="reservation.paymentMethod"
    />

    <!-- 안내 텍스트 -->
    <p class="text-xs text-muted-foreground text-center leading-relaxed">
      예약 확인 메일이 가입하신 이메일로 발송됩니다.
      <br />예약 취소 및 변경은 마이페이지 &gt; 내 예약에서 가능합니다.
    </p>

    <!-- CTA 버튼 -->
    <div class="flex flex-col sm:flex-row gap-3">
      <Button
        variant="outline"
        class="flex-1"
        aria-label="내 예약 목록 보기"
        @click="/* TODO(fe): router.push('/me') */ void 0"
      >
        내 예약 보기
      </Button>
      <Button
        class="flex-1"
        aria-label="홈으로 이동"
        @click="/* TODO(fe): router.push('/') */ void 0"
      >
        홈으로
      </Button>
    </div>
  </div>
</template>
