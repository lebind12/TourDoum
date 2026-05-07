import { del, get, patch, post } from "@/api/client";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

// ── FE 타입 ───────────────────────────────────────────────────────────────────
export type PlanItemType = "attraction" | "accommodation";

export interface PlanItem {
	id: string; // BE Long → string
	type: PlanItemType; // BE targetType lowercase
	refId: number; // BE targetId
	dayIndex: number;
	orderIndex: number;
	memo?: string;
	time?: string; // FE-only (UI 표시용, BE 미지원)
}

export interface PlanDay {
	date: string; // YYYY-MM-DD
	items: PlanItem[]; // orderIndex 오름차순
}

export interface Plan {
	id: string; // BE Long → string
	title: string;
	startDate: string; // YYYY-MM-DD
	endDate: string;
	days: PlanDay[]; // startDate~endDate 범위 + items 배치
	createdAt: string;
}

// ── BE API 응답 타입 ─────────────────────────────────────────────────────────
export interface PlanItemApiResponse {
	id: number;
	planId: number;
	dayIndex: number;
	orderIndex: number;
	targetType: "ATTRACTION" | "ACCOMMODATION";
	targetId: number;
	memo: string | null;
}

export interface PlanApiResponse {
	id: number;
	memberId: number;
	title: string;
	startDate: string; // "YYYY-MM-DD"
	endDate: string;
	createdAt: string; // ISO datetime
	items: PlanItemApiResponse[];
}

// ── 날짜 유틸 ─────────────────────────────────────────────────────────────────
function buildDays(startDate: string, endDate: string): PlanDay[] {
	const days: PlanDay[] = [];
	const cur = new Date(startDate);
	const end = new Date(endDate);
	while (cur <= end) {
		days.push({ date: cur.toISOString().slice(0, 10), items: [] });
		cur.setDate(cur.getDate() + 1);
	}
	return days;
}

// ── 매핑 함수 ─────────────────────────────────────────────────────────────────
function mapApiItem(r: PlanItemApiResponse): PlanItem {
	return {
		id: r.id.toString(),
		type: r.targetType.toLowerCase() as PlanItemType,
		refId: r.targetId,
		dayIndex: r.dayIndex,
		orderIndex: r.orderIndex,
		memo: r.memo ?? undefined,
	};
}

function mapApiToPlan(r: PlanApiResponse): Plan {
	const days = buildDays(r.startDate, r.endDate);

	// flat items → days에 배치 (orderIndex 오름차순)
	const sorted = [...r.items].sort(
		(a, b) => a.dayIndex - b.dayIndex || a.orderIndex - b.orderIndex,
	);
	for (const item of sorted) {
		if (item.dayIndex >= 0 && item.dayIndex < days.length) {
			days[item.dayIndex].items.push(mapApiItem(item));
		}
	}

	return {
		id: r.id.toString(),
		title: r.title,
		startDate: r.startDate,
		endDate: r.endDate,
		days,
		createdAt: r.createdAt,
	};
}

// ── Store ────────────────────────────────────────────────────────────────────
export const usePlansStore = defineStore("plans", () => {
	/** 내 계획 목록 (요약, items=[]). 상세는 fetchPlanById로 lazy 로드 */
	const plans = ref<Plan[]>([]);
	const loading = ref(false);
	const error = ref<string | null>(null);

	// ── computed ───────────────────────────────────────────────────────────────
	const sortedPlans = computed(() =>
		[...plans.value].sort(
			(a, b) =>
				new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
		),
	);

	function getById(id: string): Plan | undefined {
		return plans.value.find((p) => p.id === id);
	}

	// ── 목록 조회 ─────────────────────────────────────────────────────────────
	/**
	 * GET /api/plans/me — 내 여행 계획 목록 (아이템 없는 요약)
	 */
	async function fetchMyPlans(): Promise<void> {
		loading.value = true;
		error.value = null;

		const result = await get<PlanApiResponse[]>("/api/plans/me");
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "계획 목록을 불러오지 못했습니다.";
			return;
		}

		// 요약 응답(items=[])을 Plan으로 변환 — days는 날짜 범위만 빈 셸
		plans.value = result.data.map(mapApiToPlan);
	}

	/**
	 * GET /api/plans/{id} — 여행 계획 상세 (아이템 포함)
	 * 캐시에 이미 있더라도 새로 fetch해 items를 채운다.
	 */
	async function fetchPlanById(id: string): Promise<Plan | null> {
		loading.value = true;
		error.value = null;

		const result = await get<PlanApiResponse>(`/api/plans/${id}`);
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "계획을 불러오지 못했습니다.";
			return null;
		}

		const mapped = mapApiToPlan(result.data);
		// 캐시 갱신
		const idx = plans.value.findIndex((p) => p.id === id);
		if (idx >= 0) {
			plans.value[idx] = mapped;
		} else {
			plans.value.push(mapped);
		}
		return mapped;
	}

	// ── 생성 ──────────────────────────────────────────────────────────────────
	/**
	 * POST /api/plans
	 */
	async function createPlan(
		title: string,
		startDate: string,
		endDate: string,
	): Promise<Plan | null> {
		loading.value = true;
		error.value = null;

		const result = await post<PlanApiResponse>("/api/plans", {
			title,
			startDate,
			endDate,
		});
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "계획 생성에 실패했습니다.";
			return null;
		}

		const mapped = mapApiToPlan(result.data);
		plans.value.push(mapped);
		return mapped;
	}

	// ── 아이템 추가 ───────────────────────────────────────────────────────────
	/**
	 * POST /api/plans/{id}/items
	 */
	async function addItem(
		planId: string,
		dayIndex: number,
		item: Omit<PlanItem, "id" | "dayIndex" | "orderIndex">,
	): Promise<PlanItem | null> {
		const plan = getById(planId);
		if (!plan) {
			error.value = "계획을 찾을 수 없습니다.";
			return null;
		}

		const day = plan.days[dayIndex];
		const orderIndex = day ? day.items.length : 0;

		const result = await post<PlanItemApiResponse>(
			`/api/plans/${planId}/items`,
			{
				dayIndex,
				orderIndex,
				targetType: item.type.toUpperCase(),
				targetId: item.refId,
				memo: item.memo ?? null,
			},
		);

		if (result.error || !result.data) {
			error.value = result.error ?? "아이템 추가에 실패했습니다.";
			return null;
		}

		const newItem = mapApiItem(result.data);
		if (day) {
			day.items.push(newItem);
		}
		return newItem;
	}

	// ── 아이템 제거 ───────────────────────────────────────────────────────────
	/**
	 * DELETE /api/plans/{planId}/items/{itemId} — 아이템 단건 삭제.
	 * 낙관적 업데이트: 성공 전 로컬에서 제거, 실패 시 재fetch.
	 */
	async function removeItem(
		planId: string,
		dayIndex: number,
		itemId: string,
	): Promise<boolean> {
		const plan = plans.value.find((p) => p.id === planId);
		if (!plan || !plan.days[dayIndex]) return false;

		// 낙관적 로컬 제거
		const backup = [...plan.days[dayIndex].items];
		plan.days[dayIndex].items = plan.days[dayIndex].items.filter(
			(i) => i.id !== itemId,
		);

		const result = await del(`/api/plans/${planId}/items/${itemId}`);
		if (result.error) {
			// 실패 시 복원
			plan.days[dayIndex].items = backup;
			error.value = result.error;
			return false;
		}
		return true;
	}

	// ── 아이템 재정렬 ─────────────────────────────────────────────────────────
	/**
	 * PATCH /api/plans/{id}/items/reorder — drag & drop 결과 BE 반영
	 * 로컬 재정렬 먼저 → BE 전송 (낙관적 업데이트).
	 */
	async function reorderItems(
		planId: string,
		dayIndex: number,
		fromIdx: number,
		toIdx: number,
	): Promise<void> {
		const plan = plans.value.find((p) => p.id === planId);
		if (!plan || !plan.days[dayIndex]) return;
		const items = plan.days[dayIndex].items;

		if (
			fromIdx < 0 ||
			fromIdx >= items.length ||
			toIdx < 0 ||
			toIdx >= items.length ||
			fromIdx === toIdx
		)
			return;

		// 낙관적 로컬 업데이트
		const [moved] = items.splice(fromIdx, 1);
		items.splice(toIdx, 0, moved);

		// orderIndex 재할당
		items.forEach((item, i) => {
			item.orderIndex = i;
		});

		// BE 동기화 — 전체 계획의 아이템을 flat으로 직렬화
		const allEntries: { id: number; dayIndex: number; orderIndex: number }[] =
			[];
		for (const day of plan.days) {
			for (const item of day.items) {
				allEntries.push({
					id: Number(item.id),
					dayIndex: item.dayIndex,
					orderIndex: item.orderIndex,
				});
			}
		}

		const result = await patch<PlanItemApiResponse[]>(
			`/api/plans/${planId}/items/reorder`,
			{
				items: allEntries,
			},
		);

		if (result.error) {
			error.value = result.error;
			// 실패 시 BE 상태로 재동기화
			await fetchPlanById(planId);
		}
	}

	// ── 계획 삭제 ─────────────────────────────────────────────────────────────
	/**
	 * DELETE /api/plans/{id}
	 */
	async function deletePlan(planId: string): Promise<boolean> {
		loading.value = true;
		error.value = null;

		const result = await del(`/api/plans/${planId}`);
		loading.value = false;

		if (result.error) {
			error.value = result.error;
			return false;
		}

		plans.value = plans.value.filter((p) => p.id !== planId);
		return true;
	}

	return {
		plans,
		loading,
		error,
		sortedPlans,
		getById,
		fetchMyPlans,
		fetchPlanById,
		createPlan,
		addItem,
		removeItem,
		reorderItems,
		deletePlan,
	};
});
