<script setup lang="ts">
import { cn } from "@/lib/utils";
import { type VariantProps, cva } from "class-variance-authority";
import { computed } from "vue";

const buttonVariants = cva(
	// Round 7 (K): transition을 colors→[colors,transform,box-shadow]로 확장 +
	// active:scale-[0.98] 미세 press feedback. duration/easing은 `--motion-fast` /
	// `--ease-standard` 토큰 참조. focus-visible ring은 R6 결정 그대로 유지.
	// reduced-motion 사용자는 index.css의 `prefers-reduced-motion` 블록이 0.01ms로 클램프.
	"inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-[color,background-color,border-color,box-shadow,transform] duration-[var(--motion-fast)] ease-[var(--ease-standard)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 disabled:active:scale-100",
	{
		variants: {
			variant: {
				default: "bg-primary text-primary-foreground hover:bg-primary/90",
				destructive:
					"bg-destructive text-destructive-foreground hover:bg-destructive/90",
				outline:
					"border border-input bg-background hover:bg-accent hover:text-accent-foreground",
				secondary:
					"bg-secondary text-secondary-foreground hover:bg-secondary/80",
				ghost: "hover:bg-accent hover:text-accent-foreground",
				link: "text-primary underline-offset-4 hover:underline",
			},
			size: {
				default: "h-10 px-4 py-2",
				sm: "h-9 rounded-md px-3",
				lg: "h-11 rounded-md px-8",
				icon: "h-10 w-10",
			},
		},
		defaultVariants: {
			variant: "default",
			size: "default",
		},
	},
);

type ButtonVariants = VariantProps<typeof buttonVariants>;

interface Props {
	variant?: ButtonVariants["variant"];
	size?: ButtonVariants["size"];
	class?: string;
	disabled?: boolean;
	type?: "button" | "submit" | "reset";
}

const props = withDefaults(defineProps<Props>(), {
	variant: "default",
	size: "default",
	type: "button",
});

const classes = computed(() =>
	cn(buttonVariants({ variant: props.variant, size: props.size }), props.class),
);
</script>

<template>
  <button :type="type" :disabled="disabled" :class="classes">
    <slot />
  </button>
</template>
