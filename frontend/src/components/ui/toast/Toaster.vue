<script setup lang="ts">
import { useToast } from "@/composables/useToast";
import Toast from "./Toast.vue";

const { toasts, dismiss } = useToast();
</script>

<template>
  <!-- 오른쪽 하단 고정, 모바일 하단 중앙 -->
  <div
    aria-label="알림 목록"
    class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 w-80 max-sm:right-0 max-sm:left-0 max-sm:mx-4 max-sm:w-auto"
  >
    <Toast
      v-for="t in toasts"
      :key="t.id"
      :message="t.message"
      :variant="t.variant"
      :on-dismiss="() => dismiss(t.id)"
    />
  </div>
</template>
