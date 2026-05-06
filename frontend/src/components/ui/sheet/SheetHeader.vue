<script setup lang="ts">
import { cn } from "@/lib/utils";

interface Props {
	class?: string;
}
const props = defineProps<Props>();
</script>

<template>
  <div :class="cn('flex items-center justify-between border-b border-border px-4 py-3', props.class)">
    <slot />
  </div>
</template>
