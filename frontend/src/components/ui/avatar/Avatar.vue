<script setup lang="ts">
import { cn } from "@/lib/utils";
import { computed } from "vue";

type AvatarSize = "sm" | "md" | "lg";
type AvatarVariant = "sky" | "emerald" | "slate" | "primary";

interface Props {
	name: string;
	size?: AvatarSize;
	variant?: AvatarVariant;
	class?: string;
}

const props = withDefaults(defineProps<Props>(), {
	size: "md",
	variant: "slate",
});

const sizeClass: Record<AvatarSize, string> = {
	sm: "w-7 h-7 text-xs",
	md: "w-9 h-9 text-sm",
	lg: "w-11 h-11 text-base",
};

const variantClass: Record<AvatarVariant, string> = {
	primary: "bg-primary/10 text-primary",
	sky: "bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-300",
	emerald:
		"bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300",
	slate: "bg-muted text-muted-foreground",
};

const initials = computed(() => props.name.charAt(0).toUpperCase());
</script>

<template>
  <div
    :class="cn(
      'rounded-full flex items-center justify-center shrink-0 font-semibold select-none',
      sizeClass[size],
      variantClass[variant],
      props.class,
    )"
    :aria-label="name"
  >
    {{ initials }}
  </div>
</template>
