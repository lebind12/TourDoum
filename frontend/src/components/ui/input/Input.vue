<script setup lang="ts">
import { cn } from "@/lib/utils";
import { computed, useAttrs } from "vue";

interface Props {
	class?: string;
	modelValue?: string | number;
	type?: string;
	placeholder?: string;
	disabled?: boolean;
	id?: string;
	autocomplete?: string;
	required?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
	type: "text",
});

const emit = defineEmits<{
	"update:modelValue": [value: string];
}>();

const attrs = useAttrs();

const classes = computed(() =>
	cn(
		"flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
		props.class,
	),
);

function handleInput(event: Event) {
	const target = event.target as HTMLInputElement;
	emit("update:modelValue", target.value);
}
</script>

<template>
  <input
    v-bind="attrs"
    :id="id"
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :autocomplete="autocomplete"
    :required="required"
    :class="classes"
    @input="handleInput"
  />
</template>
