<script setup lang="ts">
/**
 * PriceSummary — 예약 가격 요약 카드
 *
 * Props:
 *   nightlyRate:  1박 가격 (원)
 *   nights:       숙박 박수
 *   cleaningFee:  청소비 (원, 기본값 0)
 *
 * 사용 예:
 *   <PriceSummary :nightly-rate="150000" :nights="2" :cleaning-fee="20000" />
 */
interface Props {
	nightlyRate: number;
	nights: number;
	cleaningFee?: number;
}

const props = withDefaults(defineProps<Props>(), {
	cleaningFee: 0,
});

function formatKRW(amount: number): string {
	return `${amount.toLocaleString("ko-KR")}원`;
}

const subtotal = () => props.nightlyRate * props.nights;
const total = () => subtotal() + props.cleaningFee;
</script>

<template>
  <section aria-label="가격 요약" class="rounded-lg border bg-card text-card-foreground p-4 space-y-3">
    <h3 class="text-sm font-semibold">가격 요약</h3>

    <dl class="space-y-2 text-sm">
      <!-- 1박 × 박수 -->
      <div class="flex justify-between">
        <dt class="text-muted-foreground">
          {{ formatKRW(props.nightlyRate) }} × {{ props.nights }}박
        </dt>
        <dd>{{ formatKRW(subtotal()) }}</dd>
      </div>

      <!-- 청소비 -->
      <div v-if="props.cleaningFee > 0" class="flex justify-between">
        <dt class="text-muted-foreground">청소비</dt>
        <dd>{{ formatKRW(props.cleaningFee) }}</dd>
      </div>

      <!-- 구분선 -->
      <div class="border-t pt-2 flex justify-between font-semibold">
        <dt>총 합계</dt>
        <dd class="text-primary">{{ formatKRW(total()) }}</dd>
      </div>
    </dl>
  </section>
</template>
