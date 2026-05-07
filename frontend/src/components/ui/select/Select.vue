<script setup lang="ts">
import { cn } from "@/lib/utils";
import { computed, useAttrs } from "vue";

defineOptions({ inheritAttrs: false });

interface Props {
	class?: string;
	modelValue?: string;
	disabled?: boolean;
	id?: string;
}

const props = withDefaults(defineProps<Props>(), {
	disabled: false,
});

const emit = defineEmits<{
	"update:modelValue": [value: string];
}>();

const attrs = useAttrs();

const classes = computed(() =>
	cn(
		"flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
		props.class,
	),
);

function handleChange(event: Event) {
	const target = event.target as HTMLSelectElement;
	emit("update:modelValue", target.value);
}
</script>

<template>
  <select
    v-bind="attrs"
    :id="id"
    :value="modelValue"
    :disabled="disabled"
    :class="classes"
    @change="handleChange"
  >
    <slot />
  </select>
</template>
