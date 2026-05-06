<script setup lang="ts">
import { cn } from "@/lib/utils";
import { computed } from "vue";

interface Props {
	class?: string;
	orientation?: "horizontal" | "vertical";
	decorative?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
	orientation: "horizontal",
	decorative: true,
});

const classes = computed(() =>
	cn(
		"shrink-0 bg-border",
		props.orientation === "horizontal" ? "h-[1px] w-full" : "h-full w-[1px]",
		props.class,
	),
);
</script>

<template>
  <div
    :role="decorative ? 'none' : 'separator'"
    :aria-orientation="decorative ? undefined : orientation"
    :class="classes"
  />
</template>
