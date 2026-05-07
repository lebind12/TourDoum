<script setup lang="ts">
import ReservationStepper from "@/components/reservation/ReservationStepper.vue";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
/**
 * ReservationDatesView — Step 1: 날짜·인원 선택
 *
 * 라우트: /reservations/new/:accommodationId/dates
 * TODO(fe): reservations store 연결, router.push(step2), 유효성 검사
 */
import { ref } from "vue";

// TODO(fe): reservations store 연결
const checkIn = ref("");
const checkOut = ref("");
const adults = ref(1);
const children = ref(0);

// TODO(fe): 옵션 항목(조식 등) 정의
const options = ref<string[]>([]);
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
              :min="new Date().toISOString().slice(0, 10)"
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
              :min="checkIn || new Date().toISOString().slice(0, 10)"
              aria-required="true"
              aria-label="체크아웃 날짜"
            />
          </div>
        </div>
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
      </CardContent>
    </Card>

    <!-- 하단 CTA -->
    <div class="fixed bottom-0 left-0 right-0 border-t bg-background/95 backdrop-blur px-4 py-3 sm:static sm:border-0 sm:bg-transparent sm:p-0">
      <Button
        class="w-full"
        :disabled="!checkIn || !checkOut"
        aria-label="결제 수단 선택으로 이동"
        @click="/* TODO(fe): router.push(step2) */ void 0"
      >
        다음 단계 — 결제 수단
      </Button>
    </div>
  </div>
</template>
