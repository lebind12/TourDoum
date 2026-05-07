<script setup lang="ts">
import type { Review, ReviewTargetType } from "@/stores/reviews";
import { useReviewsStore } from "@/stores/reviews";
import { computed } from "vue";
import StarRating from "./StarRating.vue";

const props = defineProps<{
	targetType: ReviewTargetType;
	targetId: number;
}>();

const store = useReviewsStore();
const items = computed(() =>
	store.getByTarget(props.targetType, props.targetId),
);
const avg = computed(() =>
	store.averageRating(props.targetType, props.targetId),
);

function formatDate(iso: string): string {
	return new Date(iso).toLocaleDateString("ko-KR", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
	});
}
</script>

<template>
  <section aria-labelledby="review-list-heading">
    <!-- 헤더 -->
    <div class="flex items-center gap-3 mb-4">
      <h3 id="review-list-heading" class="text-base font-semibold">후기</h3>
      <span v-if="items.length > 0" class="flex items-center gap-1 text-sm text-muted-foreground">
        <StarRating :model-value="avg" mode="display" />
        <span>({{ items.length }}건)</span>
      </span>
    </div>

    <!-- 빈 상태 -->
    <div
      v-if="items.length === 0"
      class="rounded-lg border border-dashed py-8 text-center text-sm text-muted-foreground"
      role="status"
    >
      아직 후기가 없습니다. 첫 번째 후기를 남겨보세요!
    </div>

    <!-- 목록 -->
    <ul v-else class="space-y-3" aria-label="후기 목록">
      <li
        v-for="review in items"
        :key="review.id"
        class="rounded-lg border bg-card p-4 space-y-2"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="space-y-0.5">
            <p class="text-sm font-medium">{{ review.authorNickname }}</p>
            <StarRating :model-value="review.rating" mode="display" />
          </div>
          <time
            :datetime="review.createdAt"
            class="text-xs text-muted-foreground shrink-0"
          >{{ formatDate(review.createdAt) }}</time>
        </div>
        <p class="text-sm text-muted-foreground leading-relaxed">{{ review.comment }}</p>
      </li>
    </ul>
  </section>
</template>
