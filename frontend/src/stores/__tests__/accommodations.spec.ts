/**
 * useAccommodationsStore — API 연결 단위 테스트
 *
 * api/client.ts의 `get` 함수를 vi.mock으로 교체.
 * BE 응답 스키마에 맞는 픽스처를 사용한다.
 */
import { useAccommodationsStore } from "@/stores/accommodations";
import type {
	AccommodationApiResponse,
	AccommodationPageResponse,
} from "@/stores/accommodations";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// api/client를 모킹
vi.mock("@/api/client", () => ({
	get: vi.fn(),
}));

import { get } from "@/api/client";
const mockGet = vi.mocked(get);

/** 테스트용 BE 응답 픽스처 */
const fixture: AccommodationApiResponse = {
	id: 1,
	name: "해운대 씨뷰 호텔",
	type: "HOTEL",
	address: "부산광역시 해운대구 해변로 1",
	lat: 35.1592,
	lng: 129.1607,
	priceFrom: 200000,
	rating: 4.5,
	thumbnailUrl: "https://example.com/img/1.jpg",
	description: "해운대 바다 앞 호텔",
	distanceMeters: null,
};

const fixture2: AccommodationApiResponse = {
	id: 2,
	name: "제주 펜션",
	type: "PENSION",
	address: "제주특별자치도 서귀포시 1",
	lat: 33.25,
	lng: 126.41,
	priceFrom: 120000,
	rating: 4.2,
	thumbnailUrl: "https://example.com/img/2.jpg",
	description: "제주 바다 뷰 펜션",
	distanceMeters: null,
};

describe("useAccommodationsStore — API 연결", () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	it("초기 상태 — items 비어있음, error null", () => {
		const store = useAccommodationsStore();
		expect(store.items).toHaveLength(0);
		expect(store.error).toBeNull();
		expect(store.loading).toBe(false);
	});

	it("fetchAccommodations — 성공 시 items에 매핑된 데이터 저장", async () => {
		const pageResponse: AccommodationPageResponse = {
			content: [fixture, fixture2],
			page: 0,
			size: 100,
			totalElements: 2,
			totalPages: 1,
			last: true,
		};
		mockGet.mockResolvedValueOnce({ data: pageResponse, error: null });

		const store = useAccommodationsStore();
		await store.fetchAccommodations();

		expect(mockGet).toHaveBeenCalledWith(
			expect.stringContaining("/api/accommodations"),
		);
		expect(store.items).toHaveLength(2);
		expect(store.error).toBeNull();

		// 필드 매핑 확인
		const first = store.items[0];
		expect(first.id).toBe(1);
		expect(first.name).toBe("해운대 씨뷰 호텔");
		expect(first.type).toBe("호텔"); // HOTEL → 호텔
		expect(first.pricePerNight).toBe(200000); // priceFrom → pricePerNight
		expect(first.imageUrl).toBe("https://example.com/img/1.jpg"); // thumbnailUrl → imageUrl
		expect(first.latitude).toBe(35.1592); // lat → latitude
		expect(first.longitude).toBe(129.1607); // lng → longitude
	});

	it("fetchAccommodations — API 실패 시 error 세트, items 비움", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 500" });

		const store = useAccommodationsStore();
		// 기존 items가 있었다면 비워야 함
		await store.fetchAccommodations();

		expect(store.items).toHaveLength(0);
		expect(store.error).toBe("HTTP 500");
	});

	it("fetchAccommodationById — 성공 시 items 캐시에 추가", async () => {
		mockGet.mockResolvedValueOnce({ data: fixture, error: null });

		const store = useAccommodationsStore();
		const result = await store.fetchAccommodationById(1);

		expect(result).not.toBeNull();
		expect(result?.id).toBe(1);
		expect(store.items).toHaveLength(1);
		expect(store.getById(1)?.name).toBe("해운대 씨뷰 호텔");
	});

	it("fetchAccommodationById — 실패 시 null 반환, error 세트", async () => {
		mockGet.mockResolvedValueOnce({ data: null, error: "HTTP 404" });

		const store = useAccommodationsStore();
		const result = await store.fetchAccommodationById(999);

		expect(result).toBeNull();
		expect(store.error).toBe("HTTP 404");
	});

	it("fetchAccommodationById — 이미 캐시에 있으면 교체", async () => {
		// 먼저 목록 로드
		const pageResponse: AccommodationPageResponse = {
			content: [fixture],
			page: 0,
			size: 100,
			totalElements: 1,
			totalPages: 1,
			last: true,
		};
		mockGet.mockResolvedValueOnce({ data: pageResponse, error: null });
		const store = useAccommodationsStore();
		await store.fetchAccommodations();
		expect(store.items).toHaveLength(1);

		// 단건 재조회 (업데이트된 데이터)
		const updated: AccommodationApiResponse = {
			...fixture,
			rating: 4.9,
		};
		mockGet.mockResolvedValueOnce({ data: updated, error: null });
		await store.fetchAccommodationById(1);

		expect(store.items).toHaveLength(1); // 중복 없이 교체
		expect(store.items[0].rating).toBe(4.9);
	});

	it("getById — items에 있는 항목 반환", async () => {
		const pageResponse: AccommodationPageResponse = {
			content: [fixture],
			page: 0,
			size: 100,
			totalElements: 1,
			totalPages: 1,
			last: true,
		};
		mockGet.mockResolvedValueOnce({ data: pageResponse, error: null });
		const store = useAccommodationsStore();
		await store.fetchAccommodations();

		expect(store.getById(1)?.name).toBe("해운대 씨뷰 호텔");
		expect(store.getById(999)).toBeUndefined();
	});

	it("filtered — searchQuery 필터 적용", async () => {
		const pageResponse: AccommodationPageResponse = {
			content: [fixture, fixture2],
			page: 0,
			size: 100,
			totalElements: 2,
			totalPages: 1,
			last: true,
		};
		mockGet.mockResolvedValueOnce({ data: pageResponse, error: null });
		const store = useAccommodationsStore();
		await store.fetchAccommodations();

		store.setSearch("해운대");
		expect(store.filtered).toHaveLength(1);
		expect(store.filtered[0].name).toBe("해운대 씨뷰 호텔");

		store.setSearch("");
		expect(store.filtered).toHaveLength(2);
	});

	it("nearby 모드 — lat/lng 전달 시 배열 응답 처리", async () => {
		mockGet.mockResolvedValueOnce({
			data: [{ ...fixture, distanceMeters: 1200 }],
			error: null,
		});

		const store = useAccommodationsStore();
		await store.fetchAccommodations(undefined, 35.16, 129.16, 3000);

		expect(store.items).toHaveLength(1);
		expect(store.items[0].distanceMeters).toBe(1200);
	});
});
