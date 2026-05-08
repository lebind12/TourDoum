<script setup lang="ts">
import { cn } from "@/lib/utils";
import { type VariantProps, cva } from "class-variance-authority";

const badgeVariants = cva(
	// R10: hardcoded `transition-colors` (tailwind 기본 150ms) → motion 토큰 일원화.
	// duration `--motion-fast` / easing `--ease-standard` — Button/Input과 동일한 micro feedback 곡선.
	"inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-[color,background-color,border-color] duration-[var(--motion-fast)] ease-[var(--ease-standard)]",
	{
		variants: {
			variant: {
				default: "bg-primary/10 text-primary",
				secondary: "bg-secondary text-secondary-foreground",
				outline: "border border-border text-foreground",
				sky: "bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-300",
				emerald:
					"bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300",
				destructive: "bg-destructive/10 text-destructive",
			},
		},
		defaultVariants: {
			variant: "default",
		},
	},
);

type BadgeVariants = VariantProps<typeof badgeVariants>;

interface Props {
	variant?: BadgeVariants["variant"];
	class?: string;
}

const props = withDefaults(defineProps<Props>(), {
	variant: "default",
});
</script>

<template>
  <span :class="cn(badgeVariants({ variant }), props.class)">
    <slot />
  </span>
</template>
