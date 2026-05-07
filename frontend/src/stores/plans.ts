import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";

export const PLANS_STORAGE_KEY = "tourdoum-plans-v1"; // gitleaks:allow

export type PlanItemType = "attraction" | "accommodation";

export interface PlanItem {
	id: string;
	type: PlanItemType;
	refId: number;
	time?: string;
	memo?: string;
}

export interface PlanDay {
	date: string; // YYYY-MM-DD
	items: PlanItem[];
}

export interface Plan {
	id: string;
	title: string;
	startDate: string; // YYYY-MM-DD
	endDate: string; // YYYY-MM-DD
	days: PlanDay[];
	createdAt: string;
}

/** 날짜 범위로 PlanDay[] 생성 */
function buildDays(start: string, end: string): PlanDay[] {
	const days: PlanDay[] = [];
	const cur = new Date(start);
	const endDate = new Date(end);
	while (cur <= endDate) {
		days.push({ date: cur.toISOString().slice(0, 10), items: [] });
		cur.setDate(cur.getDate() + 1);
	}
	return days;
}

const SEED_PLANS: Plan[] = [
	{
		id: "plan-001",
		title: "제주 3박 4일 힐링 여행",
		startDate: "2026-06-01",
		endDate: "2026-06-04",
		createdAt: "2026-05-01T10:00:00Z",
		days: [
			{
				date: "2026-06-01",
				items: [
					{
						id: "item-001",
						type: "attraction",
						refId: 4,
						time: "10:00",
						memo: "성산일출봉 등반",
					},
					{
						id: "item-002",
						type: "accommodation",
						refId: 1,
						memo: "도착 후 체크인",
					},
				],
			},
			{
				date: "2026-06-02",
				items: [
					{
						id: "item-003",
						type: "attraction",
						refId: 27,
						time: "11:00",
						memo: "천지연폭포 방문",
					},
					{
						id: "item-004",
						type: "attraction",
						refId: 42,
						time: "14:00",
						memo: "우도 페리 탑승",
					},
				],
			},
			{
				date: "2026-06-03",
				items: [
					{
						id: "item-005",
						type: "attraction",
						refId: 7,
						time: "09:00",
						memo: "한라산 영실 코스",
					},
					{
						id: "item-006",
						type: "attraction",
						refId: 13,
						time: "16:00",
						memo: "협재해수욕장 석양",
					},
				],
			},
			{
				date: "2026-06-04",
				items: [
					{
						id: "item-007",
						type: "attraction",
						refId: 18,
						time: "10:00",
						memo: "올레길 산책 후 귀가",
					},
				],
			},
		],
	},
	{
		id: "plan-002",
		title: "부산 2박 3일 바다 여행",
		startDate: "2026-07-15",
		endDate: "2026-07-17",
		createdAt: "2026-05-03T14:00:00Z",
		days: [
			{
				date: "2026-07-15",
				items: [
					{
						id: "item-011",
						type: "attraction",
						refId: 3,
						time: "13:00",
						memo: "해운대 해수욕장",
					},
					{
						id: "item-012",
						type: "accommodation",
						refId: 3,
						memo: "해운대 숙소 체크인",
					},
				],
			},
			{
				date: "2026-07-16",
				items: [
					{
						id: "item-013",
						type: "attraction",
						refId: 6,
						time: "10:00",
						memo: "광안리 해변 산책",
					},
					{
						id: "item-014",
						type: "attraction",
						refId: 9,
						time: "15:00",
						memo: "감천문화마을",
					},
					{
						id: "item-015",
						type: "attraction",
						refId: 23,
						time: "18:00",
						memo: "자갈치시장 회",
					},
				],
			},
			{
				date: "2026-07-17",
				items: [
					{
						id: "item-016",
						type: "attraction",
						refId: 39,
						time: "11:00",
						memo: "국제시장 구경 후 귀가",
					},
				],
			},
		],
	},
	{
		id: "plan-003",
		title: "서울 경복궁 문화 탐방",
		startDate: "2026-08-20",
		endDate: "2026-08-21",
		createdAt: "2026-05-05T09:00:00Z",
		days: [
			{
				date: "2026-08-20",
				items: [
					{
						id: "item-021",
						type: "attraction",
						refId: 1,
						time: "10:00",
						memo: "경복궁 관람",
					},
					{
						id: "item-022",
						type: "attraction",
						refId: 14,
						time: "14:00",
						memo: "북촌한옥마을 산책",
					},
					{
						id: "item-023",
						type: "attraction",
						refId: 8,
						time: "17:00",
						memo: "인사동 쇼핑",
					},
					{
						id: "item-024",
						type: "accommodation",
						refId: 5,
						memo: "숙소 체크인",
					},
				],
			},
			{
				date: "2026-08-21",
				items: [
					{
						id: "item-025",
						type: "attraction",
						refId: 44,
						time: "19:00",
						memo: "경복궁 야간개장",
					},
				],
			},
		],
	},
];

function loadFromStorage(): Plan[] {
	try {
		const raw = localStorage.getItem(PLANS_STORAGE_KEY);
		if (!raw) return [...SEED_PLANS];
		return JSON.parse(raw) as Plan[];
	} catch {
		return [...SEED_PLANS];
	}
}

export const usePlansStore = defineStore("plans", () => {
	const plans = ref<Plan[]>(loadFromStorage());
	const loading = ref(false);

	// localStorage 영속
	watch(
		plans,
		(val) => {
			localStorage.setItem(PLANS_STORAGE_KEY, JSON.stringify(val));
		},
		{ deep: true },
	);

	const sortedPlans = computed(() =>
		[...plans.value].sort(
			(a, b) =>
				new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
		),
	);

	function getById(id: string): Plan | undefined {
		return plans.value.find((p) => p.id === id);
	}

	function createPlan(title: string, startDate: string, endDate: string): Plan {
		const newPlan: Plan = {
			id: `plan-${Date.now()}`,
			title,
			startDate,
			endDate,
			days: buildDays(startDate, endDate),
			createdAt: new Date().toISOString(),
		};
		plans.value.push(newPlan);
		return newPlan;
	}

	function addItem(
		planId: string,
		dayIndex: number,
		item: Omit<PlanItem, "id">,
	): void {
		const plan = plans.value.find((p) => p.id === planId);
		if (!plan || !plan.days[dayIndex]) return;
		plan.days[dayIndex].items.push({ ...item, id: `item-${Date.now()}` });
	}

	function removeItem(planId: string, dayIndex: number, itemId: string): void {
		const plan = plans.value.find((p) => p.id === planId);
		if (!plan || !plan.days[dayIndex]) return;
		plan.days[dayIndex].items = plan.days[dayIndex].items.filter(
			(i) => i.id !== itemId,
		);
	}

	function deletePlan(planId: string): void {
		plans.value = plans.value.filter((p) => p.id !== planId);
	}

	return {
		plans,
		loading,
		sortedPlans,
		getById,
		createPlan,
		addItem,
		removeItem,
		deletePlan,
	};
});
