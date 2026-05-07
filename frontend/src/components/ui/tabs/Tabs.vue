<script setup lang="ts">
/**
 * Tabs — 일자별 탭 / 범용 탭 컴포넌트
 * @radix-vue 없이 순수 Vue composition 구현 (ARIA tabs 패턴)
 *
 * 사용 예 — 슬롯 기반:
 *   <Tabs :tabs="[{key:'day1', label:'Day 1'}]" v-model:active="activeKey">
 *     <template #day1>...</template>
 *   </Tabs>
 *
 * Props:
 *   tabs: { key: string; label: string; badge?: string | number }[]
 *   active: string          — v-model:active 로 제어 (없으면 첫 번째 탭)
 *   variant?: 'pills' | 'underline'  (기본: 'pills')
 *
 * Emits:
 *   update:active(key: string)
 */
import { computed } from "vue";

const props = withDefaults(
	defineProps<{
		tabs: { key: string; label: string; badge?: string | number }[];
		active: string;
		variant?: "pills" | "underline";
	}>(),
	{
		variant: "pills",
	},
);

const emit = defineEmits<{
	"update:active": [key: string];
}>();

const activeKey = computed({
	get: () => props.active ?? props.tabs[0]?.key ?? "",
	set: (v: string) => emit("update:active", v),
});

function select(key: string) {
	activeKey.value = key;
}

// 키보드 화살표로 탭 전환 (ARIA tabs 패턴)
function onKeydown(e: KeyboardEvent, idx: number) {
	const len = props.tabs.length;
	if (e.key === "ArrowRight") {
		e.preventDefault();
		const next = (idx + 1) % len;
		select(props.tabs[next].key);
		const el = document.getElementById(`tab-${props.tabs[next].key}`);
		el?.focus();
	} else if (e.key === "ArrowLeft") {
		e.preventDefault();
		const prev = (idx - 1 + len) % len;
		select(props.tabs[prev].key);
		const el = document.getElementById(`tab-${props.tabs[prev].key}`);
		el?.focus();
	} else if (e.key === "Home") {
		e.preventDefault();
		select(props.tabs[0].key);
		const el = document.getElementById(`tab-${props.tabs[0].key}`);
		el?.focus();
	} else if (e.key === "End") {
		e.preventDefault();
		const last = props.tabs[len - 1];
		select(last.key);
		const el = document.getElementById(`tab-${last.key}`);
		el?.focus();
	}
}
</script>

<template>
  <div>
    <!-- 탭 목록 -->
    <div
      class="flex gap-1.5 overflow-x-auto pb-0.5 scrollbar-none"
      role="tablist"
      :aria-label="$attrs['aria-label'] as string | undefined"
    >
      <button
        v-for="(tab, idx) in tabs"
        :id="`tab-${tab.key}`"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="activeKey === tab.key"
        :aria-controls="`tabpanel-${tab.key}`"
        :tabindex="activeKey === tab.key ? 0 : -1"
        class="relative shrink-0 inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium
               transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        :class="[
          variant === 'pills'
            ? (activeKey === tab.key
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-muted text-muted-foreground hover:bg-muted/70')
            : (activeKey === tab.key
                ? 'border-b-2 border-primary text-primary rounded-none px-3 pb-2'
                : 'border-b-2 border-transparent text-muted-foreground hover:text-foreground rounded-none px-3 pb-2')
        ]"
        @click="select(tab.key)"
        @keydown="onKeydown($event, idx)"
      >
        {{ tab.label }}
        <span
          v-if="tab.badge !== undefined"
          class="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-background/30 px-1 text-xs"
          aria-hidden="true"
        >
          {{ tab.badge }}
        </span>
      </button>
    </div>

    <!-- 탭 패널 -->
    <div
      v-for="tab in tabs"
      :id="`tabpanel-${tab.key}`"
      :key="tab.key"
      role="tabpanel"
      :aria-labelledby="`tab-${tab.key}`"
      :hidden="activeKey !== tab.key"
      class="mt-4 focus-visible:outline-none"
      tabindex="0"
    >
      <slot v-if="activeKey === tab.key" :name="tab.key" />
    </div>
  </div>
</template>
