<script setup lang="ts">
import ReservationStepper from "@/components/reservation/ReservationStepper.vue";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
	useReservationsStore,
	validateReservationDates,
} from "@/stores/reservations";
/**
 * ReservationDatesView — Step 1: 날짜·인원 선택
 *
 * 라우트: /reservations/new/:accommodationId/dates
 */
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const reservationsStore = useReservationsStore();
const accommodationId = Number(route.params.accommodationId);

const checkIn = ref("");
const checkOut = ref("");
const adults = ref(1);
const children = ref(0);
const dateError = ref("");
const guestError = ref("");

const today = new Date().toISOString().slice(0, 10);

onMounted(() => {
	if (reservationsStore.current?.accommodationId !== accommodationId) {
		reservationsStore.start(accommodationId);
	}

	const draft = reservationsStore.current;
	checkIn.value = typeof draft?.checkIn === "string" ? draft.checkIn : "";
	checkOut.value = typeof draft?.checkOut === "string" ? draft.checkOut : "";
	adults.value = typeof draft?.adults === "number" ? draft.adults : 1;
	children.value = typeof draft?.children === "number" ? draft.children : 0;
});

function validate(): boolean {
	dateError.value =
		validateReservationDates(checkIn.value, checkOut.value) ?? "";
	guestError.value =
		Number.isInteger(adults.value) && adults.value >= 1 && children.value >= 0
			? ""
			: "인원은 성인 1명 이상으로 선택해 주세요.";

	return !dateError.value && !guestError.value;
}

function goToPayment() {
	if (!validate()) return;

	try {
		reservationsStore.setDates({
			checkIn: checkIn.value,
			checkOut: checkOut.value,
		});
		reservationsStore.setGuests({
			adults: adults.value,
			children: children.value,
		});
		router.push({ name: "reservation-payment", params: { accommodationId } });
	} catch (error) {
		dateError.value =
			error instanceof Error
				? error.message
				: "예약 정보를 저장할 수 없습니다.";
	}
}
</script>

<template>
  <div class="mx-auto max-w-lg space-y-6 pb-16">
    <!-- 단계 인디케이터 -->
    <ReservationStepper :current-step="1" />

    <div>
      <h1 class="text-2xl font-semibold tracking-tight">날짜 · 인원 선택</h1>
      <p class="text-sm text-muted-foreground mt-1">체크인/체크아웃 날짜와 인원을 입력해 주세요.</p>
    </div>

    <!-- 날짜 카드 -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">숙박 일정</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="check-in">체크인</Label>
            <Input
              id="check-in"
              v-model="checkIn"
              type="date"
              :min="today"
              :aria-invalid="Boolean(dateError)"
              aria-required="true"
              aria-label="체크인 날짜"
            />
          </div>
          <div class="space-y-2">
            <Label for="check-out">체크아웃</Label>
            <Input
              id="check-out"
              v-model="checkOut"
              type="date"
              :min="checkIn || today"
              :aria-invalid="Boolean(dateError)"
              aria-required="true"
              aria-label="체크아웃 날짜"
            />
          </div>
        </div>
        <p v-if="dateError" class="text-xs text-destructive" role="alert">
          {{ dateError }}
        </p>
      </CardContent>
    </Card>

    <!-- 인원 카드 -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">인원</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <!-- 성인 -->
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium">성인</p>
            <p class="text-xs text-muted-foreground">만 13세 이상</p>
          </div>
          <div class="flex items-center gap-3" role="group" aria-label="성인 인원">
            <button
              type="button"
              class="h-8 w-8 rounded-full border flex items-center justify-center text-lg font-medium
                     hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring
                     disabled:opacity-40 disabled:pointer-events-none"
              :disabled="adults <= 1"
              aria-label="성인 감소"
              @click="adults > 1 && adults--"
            >−</button>
            <span class="w-6 text-center text-sm font-semibold" aria-live="polite">{{ adults }}</span>
            <button
              type="button"
              class="h-8 w-8 rounded-full border flex items-center justify-center text-lg font-medium
                     hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="성인 증가"
              @click="adults++"
            >+</button>
          </div>
        </div>

        <!-- 아동 -->
        <div class="flex items-center justify-between border-t pt-4">
          <div>
            <p class="text-sm font-medium">아동</p>
            <p class="text-xs text-muted-foreground">만 2~12세</p>
          </div>
          <div class="flex items-center gap-3" role="group" aria-label="아동 인원">
            <button
              type="button"
              class="h-8 w-8 rounded-full border flex items-center justify-center text-lg font-medium
                     hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring
                     disabled:opacity-40 disabled:pointer-events-none"
              :disabled="children <= 0"
              aria-label="아동 감소"
              @click="children > 0 && children--"
            >−</button>
            <span class="w-6 text-center text-sm font-semibold" aria-live="polite">{{ children }}</span>
            <button
              type="button"
              class="h-8 w-8 rounded-full border flex items-center justify-center text-lg font-medium
                     hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="아동 증가"
              @click="children++"
            >+</button>
          </div>
        </div>

        <p v-if="guestError" class="text-xs text-destructive" role="alert">
          {{ guestError }}
        </p>
      </CardContent>
    </Card>

    <!-- 하단 CTA -->
    <div class="fixed bottom-0 left-0 right-0 border-t bg-background/95 backdrop-blur px-4 py-3 sm:static sm:border-0 sm:bg-transparent sm:p-0">
      <Button
        class="w-full"
        :disabled="!checkIn || !checkOut"
        aria-label="결제 수단 선택으로 이동"
        @click="goToPayment"
      >
        다음 단계 — 결제 수단
      </Button>
    </div>
  </div>
</template>
