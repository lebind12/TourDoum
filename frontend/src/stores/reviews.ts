import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";

export const REVIEWS_STORAGE_KEY = "tourdoum-reviews-v1"; // gitleaks:allow

export type ReviewTargetType = "attraction" | "accommodation";

export interface Review {
	id: string;
	targetType: ReviewTargetType;
	targetId: number;
	authorNickname: string;
	rating: number; // 1~5
	comment: string;
	createdAt: string;
}

/** 간단한 시드 생성 헬퍼 */
function makeReview(
	id: string,
	targetType: ReviewTargetType,
	targetId: number,
	nickname: string,
	rating: number,
	comment: string,
	createdAt: string,
): Review {
	return {
		id,
		targetType,
		targetId,
		authorNickname: nickname,
		rating,
		comment,
		createdAt,
	};
}

const SEED_REVIEWS: Review[] = [
	// 여행지 (attraction) 리뷰
	makeReview(
		"rv-001",
		"attraction",
		1,
		"여행러_김민준",
		5,
		"조선의 위엄이 느껴지는 곳! 사계절 모두 아름답습니다.",
		"2026-04-10T10:00:00Z",
	),
	makeReview(
		"rv-002",
		"attraction",
		1,
		"서울사랑_박지연",
		4,
		"관광객이 많지만 그래도 꼭 한 번은 가봐야 할 곳이에요.",
		"2026-04-15T14:30:00Z",
	),
	makeReview(
		"rv-003",
		"attraction",
		1,
		"역사덕후_이승현",
		5,
		"야간개장 때 와야 진가를 느낄 수 있어요!",
		"2026-04-20T20:00:00Z",
	),
	makeReview(
		"rv-004",
		"attraction",
		2,
		"서울뷰어_최수아",
		4,
		"서울 야경이 정말 멋져요. 연인과 오기 딱 좋아요.",
		"2026-04-05T18:00:00Z",
	),
	makeReview(
		"rv-005",
		"attraction",
		2,
		"뷰맛집탐방_정호영",
		4,
		"케이블카 타고 올라가면 더 좋아요.",
		"2026-04-12T16:00:00Z",
	),
	makeReview(
		"rv-006",
		"attraction",
		3,
		"부산갈매기_강다은",
		5,
		"부산 오면 무조건 해운대! 여름엔 수영도 최고예요.",
		"2026-04-01T09:00:00Z",
	),
	makeReview(
		"rv-007",
		"attraction",
		3,
		"여름은바다_윤서진",
		5,
		"모래사장이 너무 예쁘고 물도 맑아요.",
		"2026-04-08T11:00:00Z",
	),
	makeReview(
		"rv-008",
		"attraction",
		3,
		"가족여행가_한지수",
		4,
		"아이들이 너무 좋아했어요. 다음에도 꼭 오고 싶어요.",
		"2026-04-14T13:00:00Z",
	),
	makeReview(
		"rv-009",
		"attraction",
		4,
		"제주도민_오재원",
		5,
		"일출 명소 중 최고! 새벽에 올라가도 충분히 가치있어요.",
		"2026-04-03T06:00:00Z",
	),
	makeReview(
		"rv-010",
		"attraction",
		4,
		"제주여행중_임하은",
		5,
		"유네스코 세계자연유산답게 정말 장관이에요.",
		"2026-04-11T07:00:00Z",
	),
	makeReview(
		"rv-011",
		"attraction",
		7,
		"등산러버_신동욱",
		5,
		"한라산 정상에서 보는 백록담은 평생 잊을 수 없어요.",
		"2026-04-18T08:00:00Z",
	),
	makeReview(
		"rv-012",
		"attraction",
		7,
		"자연인_장미희",
		4,
		"코스가 다양해서 체력에 맞게 선택할 수 있어 좋아요.",
		"2026-04-22T09:00:00Z",
	),
	makeReview(
		"rv-013",
		"attraction",
		10,
		"불국사팬_이동현",
		5,
		"신라의 정수를 느낄 수 있는 사찰이에요.",
		"2026-04-07T10:00:00Z",
	),
	makeReview(
		"rv-014",
		"attraction",
		10,
		"문화재탐방_김서윤",
		5,
		"석가탑과 다보탑이 마주보는 전경이 정말 멋져요.",
		"2026-04-17T11:00:00Z",
	),
	makeReview(
		"rv-015",
		"attraction",
		15,
		"한옥마을러_박준혁",
		5,
		"전주 한옥마을은 사계절 모두 아름다워요.",
		"2026-04-02T12:00:00Z",
	),
	makeReview(
		"rv-016",
		"attraction",
		15,
		"맛집탐방_최예진",
		4,
		"한옥마을 + 전주비빔밥 조합은 최고예요!",
		"2026-04-09T13:00:00Z",
	),
	makeReview(
		"rv-017",
		"attraction",
		21,
		"남이섬러_강현우",
		5,
		"드라마 겨울연가 성지순례! 봄가을이 특히 예뻐요.",
		"2026-04-06T14:00:00Z",
	),
	makeReview(
		"rv-018",
		"attraction",
		21,
		"섬여행가_임수연",
		4,
		"자전거 타고 섬 한 바퀴 돌면 최고예요.",
		"2026-04-13T15:00:00Z",
	),
	makeReview(
		"rv-019",
		"attraction",
		35,
		"독도수호자_노민수",
		5,
		"우리 땅 독도! 꼭 한 번은 방문해야 할 곳이에요.",
		"2026-04-04T16:00:00Z",
	),
	makeReview(
		"rv-020",
		"attraction",
		42,
		"우도마니아_김태양",
		5,
		"에메랄드빛 바다에 반했어요. 스노쿨링도 최고!",
		"2026-04-16T10:00:00Z",
	),
	makeReview(
		"rv-021",
		"attraction",
		42,
		"제주올레_박혜원",
		5,
		"우도 땅콩아이스크림은 꼭 드세요!",
		"2026-04-19T11:00:00Z",
	),
	makeReview(
		"rv-022",
		"attraction",
		9,
		"부산여행_이찬호",
		5,
		"알록달록 예쁜 마을이에요. 포토스팟 가득!",
		"2026-04-21T12:00:00Z",
	),
	makeReview(
		"rv-023",
		"attraction",
		9,
		"인스타감성_조아름",
		4,
		"오르막이 조금 있지만 올라갈 가치 있어요.",
		"2026-04-23T13:00:00Z",
	),
	makeReview(
		"rv-024",
		"attraction",
		11,
		"설악산러_황인준",
		5,
		"단풍시즌에 오면 정말 장관이에요!",
		"2026-04-24T09:00:00Z",
	),
	makeReview(
		"rv-025",
		"attraction",
		26,
		"수원여행_문지현",
		4,
		"성곽을 걸으며 야경 보는 것도 너무 좋아요.",
		"2026-04-25T18:00:00Z",
	),
	// 숙소 (accommodation) 리뷰
	makeReview(
		"rv-101",
		"accommodation",
		1,
		"깔끔이_이수빈",
		5,
		"청결하고 뷰가 정말 좋아요. 조식도 맛있었어요!",
		"2026-04-10T09:00:00Z",
	),
	makeReview(
		"rv-102",
		"accommodation",
		1,
		"여행계획가_박찬율",
		4,
		"위치가 좋고 직원 분들이 친절했어요.",
		"2026-04-18T10:00:00Z",
	),
	makeReview(
		"rv-103",
		"accommodation",
		2,
		"커플여행_송지아",
		5,
		"바다 전망 최고! 커플 여행에 딱이에요.",
		"2026-04-05T11:00:00Z",
	),
	makeReview(
		"rv-104",
		"accommodation",
		2,
		"제주감성_김도현",
		4,
		"인테리어가 예쁘고 편의시설이 잘 갖춰져 있어요.",
		"2026-04-15T12:00:00Z",
	),
	makeReview(
		"rv-105",
		"accommodation",
		3,
		"부산뷰_최지환",
		5,
		"광안대교 뷰는 정말 잊을 수 없어요.",
		"2026-04-08T13:00:00Z",
	),
	makeReview(
		"rv-106",
		"accommodation",
		3,
		"야경감상_이나라",
		4,
		"저녁에 야경 보면서 한잔하기 좋아요.",
		"2026-04-20T14:00:00Z",
	),
	makeReview(
		"rv-107",
		"accommodation",
		4,
		"한옥숙박_정우성",
		5,
		"한옥에서 하룻밤, 잊을 수 없는 경험이에요.",
		"2026-04-12T15:00:00Z",
	),
	makeReview(
		"rv-108",
		"accommodation",
		5,
		"강남숙박_류현진",
		4,
		"교통이 편리하고 시설이 깔끔해요.",
		"2026-04-22T16:00:00Z",
	),
];

function loadFromStorage(): Review[] {
	try {
		const raw = localStorage.getItem(REVIEWS_STORAGE_KEY);
		if (!raw) return [...SEED_REVIEWS];
		return JSON.parse(raw) as Review[];
	} catch {
		return [...SEED_REVIEWS];
	}
}

export const useReviewsStore = defineStore("reviews", () => {
	const reviews = ref<Review[]>(loadFromStorage());
	const loading = ref(false);
	const error = ref<string | null>(null);

	// localStorage 영속
	watch(
		reviews,
		(val) => {
			localStorage.setItem(REVIEWS_STORAGE_KEY, JSON.stringify(val));
		},
		{ deep: true },
	);

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

	function averageRating(
		targetType: ReviewTargetType,
		targetId: number,
	): number {
		const subset = getByTarget(targetType, targetId);
		if (subset.length === 0) return 0;
		return subset.reduce((sum, r) => sum + r.rating, 0) / subset.length;
	}

	function addReview(
		targetType: ReviewTargetType,
		targetId: number,
		authorNickname: string,
		rating: number,
		comment: string,
	): Review {
		const review: Review = {
			id: `rv-${Date.now()}`,
			targetType,
			targetId,
			authorNickname,
			rating,
			comment,
			createdAt: new Date().toISOString(),
		};
		reviews.value.unshift(review);
		return review;
	}

	function updateReview(id: string, rating: number, comment: string): boolean {
		const idx = reviews.value.findIndex((r) => r.id === id);
		if (idx === -1) return false;
		reviews.value[idx] = { ...reviews.value[idx], rating, comment };
		return true;
	}

	function deleteReview(id: string): boolean {
		const before = reviews.value.length;
		reviews.value = reviews.value.filter((r) => r.id !== id);
		return reviews.value.length < before;
	}

	const computed_reviews = computed(() => reviews.value);

	return {
		reviews: computed_reviews,
		loading,
		error,
		getByTarget,
		averageRating,
		addReview,
		updateReview,
		deleteReview,
	};
});
