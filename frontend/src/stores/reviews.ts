import { del, get, post } from "@/api/client";
import { defineStore } from "pinia";
import { ref } from "vue";

// ── BE API 응답 타입 ─────────────────────────────────────────────────────────
/** BE ReviewResponse DTO */
export interface ReviewApiResponse {
	id: number;
	memberId: number;
	targetType: string; // "ATTRACTION" | "ACCOMMODATION" (대문자)
	targetId: number;
	rating: number;
	title: string | null;
	content: string; // FE에서는 comment로 사용
	createdAt: string;
	updatedAt: string;
}

/** BE Page<ReviewResponse> 래퍼 */
export interface ReviewPageResponse {
	content: ReviewApiResponse[];
	totalElements: number;
	page: number;
	size: number;
	totalPages: number;
}

/** GET /api/reviews/summary 응답 */
export interface ReviewSummaryResponse {
	avgRating: number;
	count: number;
}

// ── FE 내부 타입 ──────────────────────────────────────────────────────────────
export type ReviewTargetType = "attraction" | "accommodation";

export interface Review {
	id: string;
	targetType: ReviewTargetType;
	targetId: number;
	/** BE 미제공 — 작성 시 auth 닉네임, 조회 시 "회원 {memberId}" 임시 표시 */
	authorNickname: string;
	memberId?: number;
	rating: number; // 1~5
	/** BE content → FE comment */
	comment: string;
	createdAt: string;
}

/** BE ReviewResponse → FE Review 매핑 */
function mapApiToReview(r: ReviewApiResponse): Review {
	return {
		id: r.id.toString(),
		targetType: r.targetType.toLowerCase() as ReviewTargetType,
		targetId: r.targetId,
		authorNickname: `회원 ${r.memberId}`, // BE 미제공 — 임시
		memberId: r.memberId,
		rating: r.rating,
		comment: r.content, // BE content → FE comment
		createdAt: r.createdAt,
	};
}

export const useReviewsStore = defineStore("reviews", () => {
	/** 조회된 리뷰 플랫 캐시 (targetType + targetId 혼합) */
	const reviews = ref<Review[]>([]);
	const loading = ref(false);
	const error = ref<string | null>(null);
	/** summary 캐시: key = "${targetType}-${targetId}" */
	const summaries = ref<Record<string, ReviewSummaryResponse>>({});

	/**
	 * 캐시에서 특정 대상의 리뷰 반환 (createdAt 내림차순).
	 * fetchByTarget 호출 후 사용.
	 */
	function getByTarget(
		targetType: ReviewTargetType,
		targetId: number,
	): Review[] {
		return reviews.value
			.filter((r) => r.targetType === targetType && r.targetId === targetId)
			.sort(
				(a, b) =>
					new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
			);
	}

	/**
	 * 캐시 기준 평균 별점.
	 * fetchSummary 결과가 있으면 그것을 우선 사용.
	 */
	function averageRating(
		targetType: ReviewTargetType,
		targetId: number,
	): number {
		const key = `${targetType}-${targetId}`;
		if (summaries.value[key]) return summaries.value[key].avgRating;
		const subset = getByTarget(targetType, targetId);
		if (subset.length === 0) return 0;
		return subset.reduce((sum, r) => sum + r.rating, 0) / subset.length;
	}

	/**
	 * 리뷰 목록 조회 — GET /api/reviews?targetType=&targetId=&page=&size=
	 * 해당 (targetType, targetId)의 기존 캐시를 교체.
	 */
	async function fetchByTarget(
		targetType: ReviewTargetType,
		targetId: number,
		page = 0,
		size = 20,
	): Promise<void> {
		loading.value = true;
		error.value = null;

		const params = new URLSearchParams({
			targetType: targetType.toUpperCase(),
			targetId: String(targetId),
			page: String(page),
			size: String(size),
		});

		const result = await get<ReviewPageResponse>(`/api/reviews?${params}`);
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "후기를 불러오지 못했습니다.";
			return;
		}

		const fetched = result.data.content.map(mapApiToReview);
		// 해당 대상 캐시 교체
		reviews.value = [
			...reviews.value.filter(
				(r) => !(r.targetType === targetType && r.targetId === targetId),
			),
			...fetched,
		];
	}

	/**
	 * 리뷰 요약 조회 — GET /api/reviews/summary?targetType=&targetId=
	 * summaries 캐시에 저장. averageRating()에서 우선 참조.
	 */
	async function fetchSummary(
		targetType: ReviewTargetType,
		targetId: number,
	): Promise<ReviewSummaryResponse | null> {
		const params = new URLSearchParams({
			targetType: targetType.toUpperCase(),
			targetId: String(targetId),
		});
		const result = await get<ReviewSummaryResponse>(
			`/api/reviews/summary?${params}`,
		);
		if (result.error || !result.data) return null;
		const key = `${targetType}-${targetId}`;
		summaries.value = { ...summaries.value, [key]: result.data };
		return result.data;
	}

	/**
	 * 리뷰 작성 — POST /api/reviews (인증 필수)
	 * 성공 시 캐시에 추가. 실패 시 null 반환.
	 *
	 * @param authorNickname - 현재 로그인 사용자 닉네임 (화면 표시용)
	 */
	async function addReview(
		targetType: ReviewTargetType,
		targetId: number,
		authorNickname: string,
		rating: number,
		comment: string,
	): Promise<Review | null> {
		loading.value = true;
		error.value = null;

		const result = await post<ReviewApiResponse>("/api/reviews", {
			targetType: targetType.toUpperCase(),
			targetId,
			rating,
			content: comment, // FE comment → BE content
		});
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "후기 등록에 실패했습니다.";
			return null;
		}

		const mapped = mapApiToReview(result.data);
		// 닉네임은 BE 미제공이므로 인자로 받은 값 사용
		mapped.authorNickname = authorNickname;
		reviews.value = [mapped, ...reviews.value];
		return mapped;
	}

	/**
	 * 리뷰 삭제 — DELETE /api/reviews/{id} (인증 필수)
	 * 성공 시 캐시에서 제거. 실패 시 false 반환.
	 */
	async function deleteReview(id: string): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await del(`/api/reviews/${id}`);
		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		reviews.value = reviews.value.filter((r) => r.id !== id);
		return true;
	}

	return {
		reviews,
		loading,
		error,
		summaries,
		getByTarget,
		averageRating,
		fetchByTarget,
		fetchSummary,
		addReview,
		deleteReview,
	};
});
