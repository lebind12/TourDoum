<script setup lang="ts">
import ReceiptCard from "@/components/reservation/ReceiptCard.vue";
/**
 * ReservationCompleteView — Step 3: 예약 완료
 *
 * 라우트: /reservations/new/:accommodationId/complete
 */
import ReservationStepper from "@/components/reservation/ReservationStepper.vue";
import { Button } from "@/components/ui/button";
import { useAccommodationsStore } from "@/stores/accommodations";
import {
	type PaymentMethod,
	calculateReservationTotal,
	useReservationsStore,
} from "@/stores/reservations";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const reservationsStore = useReservationsStore();
const accommodationsStore = useAccommodationsStore();
const reservationId = String(route.params.reservationId ?? "");

const paymentLabels: Record<PaymentMethod, string> = {
	card: "신용/체크카드",
	bank: "계좌이체",
	easy: "간편결제",
};

const receipt = computed(() => {
	const reservation = reservationsStore.confirmed.find(
		(r) => r.id === reservationId,
	);
	if (!reservation) return null;

	const accommodation = accommodationsStore.getById(
		reservation.accommodationId,
	);

	return {
		reservationId: reservation.id,
		accommodationName:
			accommodation?.name ?? `숙소 #${reservation.accommodationId}`,
		checkIn: reservation.checkIn,
		checkOut: reservation.checkOut,
		guests: reservation.adults + reservation.children,
		totalPrice: accommodation
			? calculateReservationTotal(
					accommodation.pricePerNight,
					reservation.checkIn,
					reservation.checkOut,
				)
			: 0,
		paymentMethod: paymentLabels[reservation.paymentMethod],
	};
});

function goToMe() {
	router.push("/me");
}

function goHome() {
	router.push("/");
}
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
      v-if="receipt"
      :reservation-id="receipt.reservationId"
      :accommodation-name="receipt.accommodationName"
      :check-in="receipt.checkIn"
      :check-out="receipt.checkOut"
      :guests="receipt.guests"
      :total-price="receipt.totalPrice"
      :payment-method="receipt.paymentMethod"
    />
    <p v-else class="text-sm text-destructive text-center" role="alert">
      예약 정보를 찾을 수 없습니다.
    </p>

    <!-- 안내 텍스트 -->
    <p v-if="receipt" class="text-xs text-muted-foreground text-center leading-relaxed">
      예약 확인 메일이 가입하신 이메일로 발송됩니다.
      <br />예약 취소 및 변경은 마이페이지 &gt; 내 예약에서 가능합니다.
    </p>

    <!-- CTA 버튼 -->
    <div class="flex flex-col sm:flex-row gap-3">
      <Button
        variant="outline"
        class="flex-1"
        aria-label="내 예약 목록 보기"
        @click="goToMe"
      >
        내 예약 보기
      </Button>
      <Button
        class="flex-1"
        aria-label="홈으로 이동"
        @click="goHome"
      >
        홈으로
      </Button>
    </div>
  </div>
</template>
