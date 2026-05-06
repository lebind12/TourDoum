<template>
  <button
    type="button"
    :aria-label="isFav ? '즐겨찾기 제거' : '즐겨찾기 추가'"
    :class="[
      'rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-sky-500',
      size === 'lg' ? 'p-2 bg-white/90 shadow' : 'p-1',
    ]"
    @click.prevent="toggle"
  >
    <svg
      :class="[
        'transition-colors',
        size === 'lg' ? 'w-6 h-6' : 'w-5 h-5',
        isFav ? 'text-red-500 fill-red-500' : 'text-slate-400 fill-none',
      ]"
      stroke="currentColor"
      stroke-width="1.5"
      viewBox="0 0 24 24"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"
      />
    </svg>
  </button>
</template>

<script setup lang="ts">
import { useFavoritesStore } from "@/stores/favorites";
import { computed } from "vue";

const props = withDefaults(
	defineProps<{
		attractionId: number;
		size?: "sm" | "lg";
	}>(),
	{ size: "sm" },
);

const favStore = useFavoritesStore();
const isFav = computed(() => favStore.isFavorite(props.attractionId));

async function toggle() {
	await favStore.toggleFavorite(props.attractionId);
}
</script>
