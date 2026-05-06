<script setup lang="ts">
import { cn } from "@/lib/utils";
import { computed } from "vue";

interface Props {
	class?: string;
}

const props = defineProps<Props>();
const classes = computed(() =>
	cn("text-2xl font-semibold leading-none tracking-tight", props.class),
);
</script>

<template>
  <h3 :class="classes">
    <slot />
  </h3>
</template>
