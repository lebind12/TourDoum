<script setup lang="ts">
/**
 * PaymentMethodSelector — 결제 수단 라디오 카드 선택기
 *
 * Props:
 *   modelValue: 'card' | 'transfer' | 'simple'
 *
 * Emits:
 *   update:modelValue — 선택값 변경 시
 *
 * 사용 예:
 *   <PaymentMethodSelector v-model="paymentMethod" />
 */
type PaymentMethod = "card" | "transfer" | "simple";

interface Props {
	modelValue: PaymentMethod;
}

const props = defineProps<Props>();
const emit =
	defineEmits<(e: "update:modelValue", value: PaymentMethod) => void>();

const methods: {
	id: PaymentMethod;
	label: string;
	description: string;
	icon: string;
}[] = [
	{
		id: "card",
		label: "신용/체크카드",
		description: "국내외 모든 카드 사용 가능",
		icon: "💳",
	},
	{
		id: "transfer",
		label: "계좌이체",
		description: "실시간 계좌이체 (은행 앱)",
		icon: "🏦",
	},
	{
		id: "simple",
		label: "간편결제",
		description: "카카오페이 · 네이버페이 · 토스",
		icon: "⚡",
	},
];

function select(method: PaymentMethod) {
	emit("update:modelValue", method);
}
</script>

<template>
  <fieldset class="space-y-3">
    <legend class="text-sm font-semibold mb-3">결제 수단 선택</legend>

    <label
      v-for="method in methods"
      :key="method.id"
      :for="`payment-${method.id}`"
      class="flex items-center gap-4 rounded-lg border-2 p-4 cursor-pointer transition-colors"
      :class="{
        'border-primary bg-primary/5': props.modelValue === method.id,
        'border-border bg-card hover:border-muted-foreground/40': props.modelValue !== method.id,
      }"
    >
      <input
        :id="`payment-${method.id}`"
        type="radio"
        name="payment-method"
        :value="method.id"
        :checked="props.modelValue === method.id"
        class="sr-only"
        @change="select(method.id)"
      />

      <!-- 아이콘 -->
      <span class="text-2xl" aria-hidden="true">{{ method.icon }}</span>

      <!-- 텍스트 -->
      <span class="flex-1">
        <span class="block text-sm font-medium">{{ method.label }}</span>
        <span class="block text-xs text-muted-foreground mt-0.5">{{ method.description }}</span>
      </span>

      <!-- 선택 표시 원 -->
      <span
        class="h-5 w-5 rounded-full border-2 flex items-center justify-center flex-shrink-0"
        :class="{
          'border-primary': props.modelValue === method.id,
          'border-border': props.modelValue !== method.id,
        }"
        aria-hidden="true"
      >
        <span
          v-if="props.modelValue === method.id"
          class="h-2.5 w-2.5 rounded-full bg-primary"
        />
      </span>
    </label>
  </fieldset>
</template>
