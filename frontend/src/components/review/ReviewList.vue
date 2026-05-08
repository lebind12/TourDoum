<script setup lang="ts">
import type { Review, ReviewTargetType } from "@/stores/reviews";
import { useReviewsStore } from "@/stores/reviews";
import { computed, onMounted } from "vue";
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

onMounted(() => {
	store.fetchByTarget(props.targetType, props.targetId);
});

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
    <!-- 헤더: 제목 + 평균 별점 요약 -->
    <div class="flex items-center gap-3 mb-4">
      <h3 id="review-list-heading" class="text-base font-semibold">후기</h3>
      <span v-if="items.length > 0" class="flex items-center gap-1.5 text-sm text-muted-foreground">
        <StarRating :model-value="avg" mode="display" size="sm" />
        <span class="font-medium text-foreground">{{ avg.toFixed(1) }}</span>
        <span class="text-muted-foreground">({{ items.length }}건)</span>
      </span>
    </div>

    <!-- 빈 상태 — "첫 후기를 남겨보세요" CTA -->
    <div
      v-if="items.length === 0"
      class="flex flex-col items-center gap-3 rounded-xl border border-dashed bg-muted/30 py-10 text-center"
      role="status"
      aria-label="후기 없음"
    >
      <span class="text-3xl" aria-hidden="true">⭐</span>
      <div class="space-y-0.5">
        <p class="text-sm font-medium text-foreground">아직 후기가 없습니다.</p>
        <p class="text-xs text-muted-foreground">첫 후기를 남겨보세요!</p>
      </div>
      <!-- 후기 작성 CTA 슬롯 (상위 컴포넌트에서 삽입 가능) -->
      <slot name="cta" />
    </div>

    <!-- 목록 -->
    <ul v-else class="space-y-3" aria-label="후기 목록">
      <li
        v-for="review in items"
        :key="review.id"
        class="rounded-xl border bg-card p-4 shadow-sm transition-shadow hover:shadow-md"
      >
        <!-- 작성자 행: 아바타 + 닉네임 + 별점 + 날짜 -->
        <div class="flex items-start gap-3">
          <!-- 아바타 (이니셜 기반) -->
          <div
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary select-none"
            aria-hidden="true"
          >
            {{ (review.authorNickname?.charAt(0) ?? "?").toUpperCase() }}
          </div>

          <div class="min-w-0 flex-1">
            <!-- 닉네임 + 날짜 -->
            <div class="flex items-center justify-between gap-2 mb-1">
              <p class="text-sm font-medium truncate">{{ review.authorNickname }}</p>
              <time
                :datetime="review.createdAt"
                class="text-xs text-muted-foreground shrink-0"
              >{{ formatDate(review.createdAt) }}</time>
            </div>

            <!-- 별점 -->
            <StarRating :model-value="review.rating" mode="display" size="sm" />
          </div>
        </div>

        <!-- 후기 내용 -->
        <p class="mt-3 text-sm text-muted-foreground leading-relaxed pl-12">
          {{ review.comment }}
        </p>
      </li>
    </ul>

    <!-- 후기 작성 CTA (리뷰가 있을 때도 노출) -->
    <div v-if="items.length > 0" class="flex justify-end pt-2">
      <slot name="cta" />
    </div>
  </section>
</template>
