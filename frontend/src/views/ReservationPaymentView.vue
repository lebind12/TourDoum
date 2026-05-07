<script setup lang="ts">
import PaymentMethodSelector from "@/components/reservation/PaymentMethodSelector.vue";
import PriceSummary from "@/components/reservation/PriceSummary.vue";
import ReservationStepper from "@/components/reservation/ReservationStepper.vue";
import { Button } from "@/components/ui/button";
/**
 * ReservationPaymentView — Step 2: 결제 수단 선택
 *
 * 라우트: /reservations/new/:accommodationId/payment
 * TODO(fe): reservations store에서 날짜/인원/가격 읽기, 결제 처리, router.push(complete)
 */
import { ref } from "vue";

type PaymentMethod = "card" | "transfer" | "simple";

// TODO(fe): reservations store에서 읽기
const selectedPayment = ref<PaymentMethod>("card");

// TODO(fe): store에서 계산된 가격 읽기 — placeholder
const nightlyRate = ref(150000);
const nights = ref(2);
const cleaningFee = ref(20000);
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6 pb-16">
    <!-- 단계 인디케이터 -->
    <ReservationStepper :current-step="2" />

    <div>
      <h1 class="text-2xl font-semibold tracking-tight">결제 수단 선택</h1>
      <p class="text-sm text-muted-foreground mt-1">결제 수단을 선택하고 예약을 완료해 주세요.</p>
    </div>

    <!-- 가격 요약 -->
    <PriceSummary
      :nightly-rate="nightlyRate"
      :nights="nights"
      :cleaning-fee="cleaningFee"
    />

    <!-- 결제 수단 -->
    <PaymentMethodSelector v-model="selectedPayment" />

    <!-- 약관 안내 -->
    <p class="text-xs text-muted-foreground leading-relaxed">
      예약 확인 버튼을 누르면 TourDoum의
      <a href="#" class="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring rounded">이용약관</a> 및
      <a href="#" class="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring rounded">개인정보처리방침</a>에 동의하는 것으로 간주됩니다.
    </p>

    <!-- 하단 CTA -->
    <div class="fixed bottom-0 left-0 right-0 border-t bg-background/95 backdrop-blur px-4 py-3 sm:static sm:border-0 sm:bg-transparent sm:p-0 flex gap-3">
      <Button
        variant="outline"
        class="flex-1"
        aria-label="이전 단계로 이동"
        @click="/* TODO(fe): router.back() */ void 0"
      >
        이전
      </Button>
      <Button
        class="flex-1"
        aria-label="예약 확인 및 결제"
        @click="/* TODO(fe): 결제 처리 → router.push(complete) */ void 0"
      >
        예약 확인
      </Button>
    </div>
  </div>
</template>
