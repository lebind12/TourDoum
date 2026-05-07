import { defineStore } from "pinia";
import { ref, watch } from "vue";
import { useAccommodationsStore } from "./accommodations";
import { useAttractionsStore } from "./attractions";
import { usePlansStore } from "./plans";
import { useReviewsStore } from "./reviews";

export const SEARCH_RECENT_KEY = "tourdoum-search-recent-v1"; // gitleaks:allow
const MAX_RECENT = 5;

export interface SearchAttractionResult {
	type: "attraction";
	id: number;
	name: string;
	category: string;
	imageUrl: string;
	sido: string;
}

export interface SearchAccommodationResult {
	type: "accommodation";
	id: number;
	name: string;
	accommodationType: string;
	imageUrl: string;
	pricePerNight: number;
}

export interface SearchPlanResult {
	type: "plan";
	id: string;
	title: string;
	startDate: string;
	endDate: string;
}

export interface SearchReviewResult {
	type: "review";
	id: string;
	comment: string;
	authorNickname: string;
	rating: number;
	targetType: string;
	targetId: number;
}

export interface SearchResults {
	attractions: SearchAttractionResult[];
	accommodations: SearchAccommodationResult[];
	plans: SearchPlanResult[];
	reviews: SearchReviewResult[];
}

function loadRecent(): string[] {
	try {
		const raw = localStorage.getItem(SEARCH_RECENT_KEY);
		if (!raw) return [];
		return JSON.parse(raw) as string[];
	} catch {
		return [];
	}
}

export const useSearchStore = defineStore("search", () => {
	const query = ref("");
	const results = ref<SearchResults>({
		attractions: [],
		accommodations: [],
		plans: [],
		reviews: [],
	});
	const loading = ref(false);
	const recentQueries = ref<string[]>(loadRecent());

	watch(recentQueries, (val) => {
		localStorage.setItem(SEARCH_RECENT_KEY, JSON.stringify(val));
	});

	function addRecent(q: string) {
		const trimmed = q.trim();
		if (!trimmed) return;
		const list = recentQueries.value.filter((r) => r !== trimmed);
		list.unshift(trimmed);
		recentQueries.value = list.slice(0, MAX_RECENT);
	}

	function removeRecent(q: string) {
		recentQueries.value = recentQueries.value.filter((r) => r !== q);
	}

	function clearRecent() {
		recentQueries.value = [];
	}

	function search(q: string) {
		const trimmed = q.trim();
		query.value = trimmed;

		if (!trimmed) {
			results.value = {
				attractions: [],
				accommodations: [],
				plans: [],
				reviews: [],
			};
			return;
		}

		addRecent(trimmed);

		const lq = trimmed.toLowerCase();

		// 여행지
		const attractionsStore = useAttractionsStore();
		const attractions: SearchAttractionResult[] = attractionsStore.items
			.filter(
				(a) =>
					a.name.toLowerCase().includes(lq) ||
					a.category.toLowerCase().includes(lq) ||
					a.sido.toLowerCase().includes(lq),
			)
			.slice(0, 6)
			.map((a) => ({
				type: "attraction",
				id: a.id,
				name: a.name,
				category: a.category,
				imageUrl: a.imageUrl,
				sido: a.sido,
			}));

		// 숙박
		const accommodationsStore = useAccommodationsStore();
		const accommodations: SearchAccommodationResult[] =
			accommodationsStore.items
				.filter(
					(a) =>
						a.name.toLowerCase().includes(lq) ||
						a.type.toLowerCase().includes(lq),
				)
				.slice(0, 6)
				.map((a) => ({
					type: "accommodation",
					id: a.id,
					name: a.name,
					accommodationType: a.type,
					imageUrl: a.imageUrl,
					pricePerNight: a.pricePerNight,
				}));

		// 여행 계획
		const plansStore = usePlansStore();
		const plans: SearchPlanResult[] = plansStore.plans
			.filter((p) => p.title.toLowerCase().includes(lq))
			.slice(0, 6)
			.map((p) => ({
				type: "plan",
				id: p.id,
				title: p.title,
				startDate: p.startDate,
				endDate: p.endDate,
			}));

		// 후기
		const reviewsStore = useReviewsStore();
		const reviews: SearchReviewResult[] = reviewsStore.reviews
			.filter(
				(r) =>
					r.comment.toLowerCase().includes(lq) ||
					r.authorNickname.toLowerCase().includes(lq),
			)
			.slice(0, 6)
			.map((r) => ({
				type: "review",
				id: r.id,
				comment: r.comment,
				authorNickname: r.authorNickname,
				rating: r.rating,
				targetType: r.targetType,
				targetId: r.targetId,
			}));

		results.value = { attractions, accommodations, plans, reviews };
	}

	const totalCount = () =>
		results.value.attractions.length +
		results.value.accommodations.length +
		results.value.plans.length +
		results.value.reviews.length;

	return {
		query,
		results,
		loading,
		recentQueries,
		search,
		addRecent,
		removeRecent,
		clearRecent,
		totalCount,
	};
});
