<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth";
import type { ReviewTargetType } from "@/stores/reviews";
import { useReviewsStore } from "@/stores/reviews";
import { ref } from "vue";
import { useRouter } from "vue-router";
import StarRating from "./StarRating.vue";

const props = defineProps<{
	targetType: ReviewTargetType;
	targetId: number;
}>();

const authStore = useAuthStore();
const reviewsStore = useReviewsStore();
const router = useRouter();

const rating = ref(5);
const comment = ref("");
const submitting = ref(false);
const submitted = ref(false);
const errorMsg = ref<string | null>(null);

async function submit() {
	if (!authStore.currentUser) {
		router.push({ name: "login" });
		return;
	}
	if (comment.value.trim().length < 5) {
		errorMsg.value = "후기는 5자 이상 작성해 주세요.";
		return;
	}
	errorMsg.value = null;
	submitting.value = true;
	const result = await reviewsStore.addReview(
		props.targetType,
		props.targetId,
		rating.value,
		comment.value.trim(),
	);
	submitting.value = false;
	if (!result) {
		errorMsg.value = reviewsStore.error ?? "후기 등록에 실패했습니다.";
		return;
	}
	submitted.value = true;
	comment.value = "";
	rating.value = 5;
}
</script>

<template>
  <section aria-labelledby="review-form-heading" class="mt-4">
    <h3 id="review-form-heading" class="text-base font-semibold mb-3">후기 작성</h3>

    <!-- 비로그인 -->
    <div
      v-if="!authStore.currentUser"
      class="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground"
    >
      <RouterLink to="/login" class="underline underline-offset-2 text-primary">로그인</RouterLink> 후 후기를 남길 수 있습니다.
    </div>

    <!-- 작성 완료 메시지 -->
    <div
      v-else-if="submitted"
      class="rounded-lg bg-green-50 dark:bg-green-950/30 border border-green-200 dark:border-green-900 p-4 text-sm text-green-700 dark:text-green-400"
      role="status"
      aria-live="polite"
    >
      후기가 등록되었습니다! 감사합니다.
      <button
        type="button"
        class="ml-2 underline underline-offset-2"
        @click="submitted = false"
      >
        추가 작성
      </button>
    </div>

    <!-- 폼 -->
    <form
      v-else
      class="rounded-lg border bg-card p-4 space-y-3"
      @submit.prevent="submit"
    >
      <!-- 별점 -->
      <div class="space-y-1">
        <label class="text-sm font-medium">별점</label>
        <StarRating v-model="rating" mode="input" />
      </div>

      <!-- 내용 -->
      <div class="space-y-1">
        <label for="review-comment" class="text-sm font-medium">내용</label>
        <textarea
          id="review-comment"
          v-model="comment"
          rows="3"
          placeholder="이 곳에 대한 솔직한 후기를 남겨주세요. (5자 이상)"
          class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none"
          aria-required="true"
          :aria-invalid="!!errorMsg"
          :aria-describedby="errorMsg ? 'review-error' : undefined"
        />
        <p
          v-if="errorMsg"
          id="review-error"
          role="alert"
          class="text-xs text-destructive"
        >{{ errorMsg }}</p>
      </div>

      <Button type="submit" :disabled="submitting" class="w-full">
        {{ submitting ? '등록 중...' : '후기 등록' }}
      </Button>
    </form>
  </section>
</template>
