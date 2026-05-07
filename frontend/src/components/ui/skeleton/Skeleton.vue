<script setup lang="ts">
/**
 * Skeleton — 로딩 shimmer 스켈레톤 컴포넌트
 *
 * Props:
 *   class?: string — 추가 클래스 (width/height/rounded 지정)
 *
 * 사용 예:
 *   <Skeleton class="h-4 w-32 rounded" />
 *   <Skeleton class="h-10 w-full rounded-xl" />
 */
defineProps<{
	class?: string;
}>();
</script>

<template>
  <div
    :class="[
      'animate-pulse rounded-md bg-muted/60',
      $props.class,
    ]"
    aria-hidden="true"
    role="presentation"
  />
</template>
