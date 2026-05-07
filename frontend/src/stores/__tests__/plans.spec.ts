/**
 * usePlansStore — API 연결 단위 테스트
 *
 * api/client.ts의 get/post/patch/del 함수를 vi.mock으로 교체.
 */
import {
	type PlanApiResponse,
	type PlanItemApiResponse,
	usePlansStore,
} from "@/stores/plans";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/api/client", () => ({
	get: vi.fn(),
	post: vi.fn(),
	patch: vi.fn(),
	del: vi.fn(),
}));

import { del, get, patch, post } from "@/api/client";
const mockGet = vi.mocked(get);
const mockPost = vi.mocked(post);
const mockPatch = vi.mocked(patch);
const mockDel = vi.mocked(del);

// ── 픽스처 ────────────────────────────────────────────────────────────────────
const itemFixture: PlanItemApiResponse = {
	id: 10,
	planId: 1,
	dayIndex: 0,
	orderIndex: 0,
	targetType: "ATTRACTION",
	targetId: 4,
	memo: "성산일출봉 등반",
};

const item2Fixture: PlanItemApiResponse = {
	id: 11,
	planId: 1,
	dayIndex: 0,
	orderIndex: 1,
	targetType: "ACCOMMODATION",
	targetId: 1,
	memo: null,
};

const planFixture: PlanApiResponse = {
	id: 1,
	memberId: 10,
	title: "제주 3박 4일",
	startDate: "2026-06-01",
	endDate: "2026-06-04",
	createdAt: "2026-05-07T10:00:00",
	items: [],
};

const planDetailFixture: PlanApiResponse = {
	...planFixture,
	items: [itemFixture, item2Fixture],
};

describe("usePlansStore — 초기 상태", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
	});

	it("plans는 빈 배열로 시작한다", () => {
		const store = usePlansStore();
		expect(store.plans).toHaveLength(0);
	});

	it("loading, error 초기값", () => {
		const store = usePlansStore();
		expect(store.loading).toBe(false);
		expect(store.error).toBeNull();
	});
});

describe("usePlansStore — fetchMyPlans", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 plans 갱신", async () => {
		mockGet.mockResolvedValueOnce({ data: [planFixture], error: null });

		const store = usePlansStore();
		await store.fetchMyPlans();

		expect(mockGet).toHaveBeenCalledWith("/api/plans/me");
		expect(store.plans).toHaveLength(1);
		expect(store.plans[0].id).toBe("1");
		expect(store.plans[0].title).toBe("제주 3박 4일");
		// 요약 응답 — items 없어도 days 범위 생성 (6/1~6/4 = 4일)
		expect(store.plans[0].days).toHaveLength(4);
	});

	it("실패 시 error 세트", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 401" });

		const store = usePlansStore();
		await store.fetchMyPlans();

		expect(store.error).toBe("HTTP 401");
		expect(store.plans).toHaveLength(0);
	});
});

describe("usePlansStore — fetchPlanById", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 아이템 포함 Plan 반환 + 캐시 저장", async () => {
		mockGet.mockResolvedValueOnce({ data: planDetailFixture, error: null });

		const store = usePlansStore();
		const plan = await store.fetchPlanById("1");

		expect(mockGet).toHaveBeenCalledWith("/api/plans/1");
		expect(plan).not.toBeNull();
		expect(plan?.days[0].items).toHaveLength(2);
		// targetType 대문자 → 소문자
		expect(plan?.days[0].items[0].type).toBe("attraction");
		// targetId → refId
		expect(plan?.days[0].items[0].refId).toBe(4);
		// id Long → string
		expect(plan?.days[0].items[0].id).toBe("10");
		// 캐시 저장
		expect(store.getById("1")).toBeDefined();
	});

	it("실패 시 null 반환, error 세트", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 404" });

		const store = usePlansStore();
		const plan = await store.fetchPlanById("999");

		expect(plan).toBeNull();
		expect(store.error).toBe("HTTP 404");
	});

	it("기존 캐시 항목을 상세로 덮어쓴다", async () => {
		// 먼저 요약 캐시
		mockGet.mockResolvedValueOnce({ data: [planFixture], error: null });
		const store = usePlansStore();
		await store.fetchMyPlans();
		expect(store.plans[0].days[0].items).toHaveLength(0);

		// 상세 fetch
		mockGet.mockResolvedValueOnce({ data: planDetailFixture, error: null });
		await store.fetchPlanById("1");
		expect(store.plans[0].days[0].items).toHaveLength(2);
	});
});

describe("usePlansStore — createPlan", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 Plan 반환 + plans에 추가", async () => {
		mockPost.mockResolvedValueOnce({ data: planFixture, error: null });

		const store = usePlansStore();
		const plan = await store.createPlan(
			"제주 3박 4일",
			"2026-06-01",
			"2026-06-04",
		);

		expect(mockPost).toHaveBeenCalledWith("/api/plans", {
			title: "제주 3박 4일",
			startDate: "2026-06-01",
			endDate: "2026-06-04",
		});
		expect(plan).not.toBeNull();
		expect(plan?.id).toBe("1");
		expect(store.plans).toHaveLength(1);
	});

	it("실패 시 null, error 세트", async () => {
		mockPost.mockResolvedValueOnce({ data: null, error: "HTTP 400" });

		const store = usePlansStore();
		const plan = await store.createPlan("테스트", "2026-09-01", "2026-09-03");

		expect(plan).toBeNull();
		expect(store.error).toBe("HTTP 400");
	});
});

describe("usePlansStore — addItem", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 day에 아이템 추가", async () => {
		mockPost.mockResolvedValueOnce({ data: planFixture, error: null });
		const store = usePlansStore();
		await store.createPlan("제주 3박 4일", "2026-06-01", "2026-06-04");

		mockPost.mockResolvedValueOnce({ data: itemFixture, error: null });
		const item = await store.addItem("1", 0, {
			type: "attraction",
			refId: 4,
			memo: "성산일출봉 등반",
		});

		expect(mockPost).toHaveBeenLastCalledWith("/api/plans/1/items", {
			dayIndex: 0,
			orderIndex: 0,
			targetType: "ATTRACTION",
			targetId: 4,
			memo: "성산일출봉 등반",
		});
		expect(item).not.toBeNull();
		expect(item?.type).toBe("attraction");
		expect(store.getById("1")?.days[0].items).toHaveLength(1);
	});

	it("계획 없으면 null 반환", async () => {
		const store = usePlansStore();
		const item = await store.addItem("999", 0, {
			type: "attraction",
			refId: 1,
		});
		expect(item).toBeNull();
		expect(store.error).toBeTruthy();
	});
});

describe("usePlansStore — removeItem (클라이언트 사이드)", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("아이템을 로컬에서 제거한다 (BE 호출 없음)", async () => {
		mockGet.mockResolvedValueOnce({ data: planDetailFixture, error: null });
		const store = usePlansStore();
		await store.fetchPlanById("1");

		expect(store.getById("1")?.days[0].items).toHaveLength(2);

		store.removeItem("1", 0, "10");

		expect(store.getById("1")?.days[0].items).toHaveLength(1);
		expect(store.getById("1")?.days[0].items[0].id).toBe("11");
		// BE 호출 없음
		expect(mockDel).not.toHaveBeenCalled();
	});
});

describe("usePlansStore — reorderItems", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("로컬 재정렬 후 PATCH 전송", async () => {
		mockGet.mockResolvedValueOnce({ data: planDetailFixture, error: null });
		const store = usePlansStore();
		await store.fetchPlanById("1");

		mockPatch.mockResolvedValueOnce({ data: [], error: null });

		// index 0 → index 1 이동
		await store.reorderItems("1", 0, 0, 1);

		expect(mockPatch).toHaveBeenCalledWith(
			"/api/plans/1/items/reorder",
			expect.objectContaining({
				items: expect.arrayContaining([
					expect.objectContaining({ id: 10 }),
					expect.objectContaining({ id: 11 }),
				]),
			}),
		);

		const items = store.getById("1")?.days[0].items;
		// 재정렬 후 item 11이 먼저
		expect(items?.[0].id).toBe("11");
		expect(items?.[1].id).toBe("10");
	});

	it("fromIdx === toIdx 이면 PATCH 호출 없음", async () => {
		mockGet.mockResolvedValueOnce({ data: planDetailFixture, error: null });
		const store = usePlansStore();
		await store.fetchPlanById("1");

		await store.reorderItems("1", 0, 0, 0);

		expect(mockPatch).not.toHaveBeenCalled();
	});
});

describe("usePlansStore — deletePlan", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});
	afterEach(() => vi.clearAllMocks());

	it("성공 시 plans에서 제거", async () => {
		mockGet.mockResolvedValueOnce({ data: [planFixture], error: null });
		const store = usePlansStore();
		await store.fetchMyPlans();

		mockDel.mockResolvedValueOnce({ data: null, error: null });
		const success = await store.deletePlan("1");

		expect(mockDel).toHaveBeenCalledWith("/api/plans/1");
		expect(success).toBe(true);
		expect(store.plans).toHaveLength(0);
	});

	it("실패 시 false, plans 유지", async () => {
		mockGet.mockResolvedValueOnce({ data: [planFixture], error: null });
		const store = usePlansStore();
		await store.fetchMyPlans();

		mockDel.mockResolvedValueOnce({ data: null, error: "HTTP 403" });
		const success = await store.deletePlan("1");

		expect(success).toBe(false);
		expect(store.error).toBe("HTTP 403");
		expect(store.plans).toHaveLength(1);
	});
});

describe("usePlansStore — sortedPlans", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	it("createdAt 내림차순으로 정렬된다", async () => {
		const older: PlanApiResponse = {
			...planFixture,
			id: 2,
			title: "오래된 계획",
			createdAt: "2026-04-01T09:00:00",
		};
		mockGet.mockResolvedValueOnce({
			data: [older, planFixture],
			error: null,
		});
		const store = usePlansStore();
		await store.fetchMyPlans();

		const sorted = store.sortedPlans;
		expect(sorted[0].id).toBe("1"); // planFixture 더 최신
		expect(sorted[1].id).toBe("2");
	});
});
