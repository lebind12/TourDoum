import { PLANS_STORAGE_KEY, usePlansStore } from "@/stores/plans";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// localStorage mock
const localStorageMock = (() => {
	let store: Record<string, string> = {};
	return {
		getItem: vi.fn((key: string) => store[key] ?? null),
		setItem: vi.fn((key: string, value: string) => {
			store[key] = value;
		}),
		removeItem: vi.fn((key: string) => {
			delete store[key];
		}),
		clear: vi.fn(() => {
			store = {};
		}),
	};
})();

vi.stubGlobal("localStorage", localStorageMock);

describe("usePlansStore", () => {
	beforeEach(() => {
		localStorageMock.clear();
		setActivePinia(createPinia());
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("시드 데이터 3개가 초기 로드된다", () => {
		const store = usePlansStore();
		expect(store.plans.length).toBeGreaterThanOrEqual(3);
	});

	it("sortedPlans는 createdAt 내림차순으로 정렬된다", () => {
		const store = usePlansStore();
		const sorted = store.sortedPlans;
		for (let i = 0; i < sorted.length - 1; i++) {
			expect(new Date(sorted[i].createdAt).getTime()).toBeGreaterThanOrEqual(
				new Date(sorted[i + 1].createdAt).getTime(),
			);
		}
	});

	it("createPlan — 새 계획을 추가하고 반환한다", () => {
		const store = usePlansStore();
		const before = store.plans.length;
		const plan = store.createPlan("테스트 여행", "2026-08-01", "2026-08-03");
		expect(store.plans.length).toBe(before + 1);
		expect(plan.title).toBe("테스트 여행");
		expect(plan.days).toHaveLength(3); // 8/1, 8/2, 8/3
		expect(plan.id).toMatch(/^plan-\d+$/);
	});

	it("getById — 존재하는 id면 plan을 반환한다", () => {
		const store = usePlansStore();
		const plan = store.createPlan("조회 테스트", "2026-09-01", "2026-09-02");
		const found = store.getById(plan.id);
		expect(found).toBeDefined();
		expect(found?.title).toBe("조회 테스트");
	});

	it("getById — 없는 id면 undefined를 반환한다", () => {
		const store = usePlansStore();
		expect(store.getById("nonexistent-id")).toBeUndefined();
	});

	it("addItem — 지정 일자에 아이템을 추가한다", () => {
		const store = usePlansStore();
		const plan = store.createPlan(
			"아이템 추가 테스트",
			"2026-10-01",
			"2026-10-02",
		);
		store.addItem(plan.id, 0, {
			type: "attraction",
			refId: 1,
			time: "10:00",
			memo: "경복궁",
		});
		const updated = store.getById(plan.id);
		if (!updated) throw new Error("plan not found");
		expect(updated.days[0].items).toHaveLength(1);
		expect(updated.days[0].items[0].refId).toBe(1);
	});

	it("removeItem — 아이템을 제거한다", () => {
		const store = usePlansStore();
		const plan = store.createPlan(
			"아이템 제거 테스트",
			"2026-10-10",
			"2026-10-11",
		);
		store.addItem(plan.id, 0, { type: "attraction", refId: 2 });
		const found = store.getById(plan.id);
		const item = found?.days[0].items[0];
		if (!item) throw new Error("item not found");
		store.removeItem(plan.id, 0, item.id);
		expect(store.getById(plan.id)?.days[0].items).toHaveLength(0);
	});

	it("deletePlan — 계획을 삭제한다", () => {
		const store = usePlansStore();
		const plan = store.createPlan("삭제 테스트", "2026-11-01", "2026-11-01");
		const before = store.plans.length;
		store.deletePlan(plan.id);
		expect(store.plans.length).toBe(before - 1);
		expect(store.getById(plan.id)).toBeUndefined();
	});

	it("createPlan 후 localStorage에 저장된다", async () => {
		const store = usePlansStore();
		store.createPlan("localStorage 테스트", "2026-12-01", "2026-12-01");
		// watch는 다음 tick에 실행됨
		await new Promise((r) => setTimeout(r, 0));
		expect(localStorageMock.setItem).toHaveBeenCalledWith(
			PLANS_STORAGE_KEY,
			expect.any(String),
		);
	});
});
