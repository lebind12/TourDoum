import { get } from "@/api/client";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

// ── BE API 응답 타입 ─────────────────────────────────────────────────────────
/** BE AccommodationType enum 값 */
export type AccommodationApiType = "HOTEL" | "PENSION" | "GUESTHOUSE" | "MOTEL";

/** GET /api/accommodations 및 GET /api/accommodations/{id} 응답 DTO */
export interface AccommodationApiResponse {
	id: number;
	name: string;
	type: AccommodationApiType;
	address: string;
	/** 시·도 (be #43 V16 도입) */
	sido: string;
	/** 시·군·구 (be #43 V16 도입) */
	gugun: string;
	lat: number;
	lng: number;
	priceFrom: number;
	rating: number;
	thumbnailUrl: string;
	description: string;
	distanceMeters: number | null;
}

/** GET /api/accommodations/regions 응답 (be #43) */
export interface AccommodationRegionsApiResponse {
	sidos: string[];
	gugunsBySido: Record<string, string[]>;
}

/** GET /api/accommodations (페이지 모드) 래퍼 */
export interface AccommodationPageResponse {
	content: AccommodationApiResponse[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
	last: boolean;
}

// ── FE 내부 타입 ──────────────────────────────────────────────────────────────
/** BE 타입 → 한국어 레이블 매핑 */
const TYPE_LABEL: Record<AccommodationApiType, Accommodation["type"]> = {
	HOTEL: "호텔",
	PENSION: "펜션",
	GUESTHOUSE: "게스트하우스",
	MOTEL: "모텔",
};

export interface Accommodation {
	id: number;
	name: string;
	/** 한국어 표시용 타입 */
	type: "호텔" | "펜션" | "게스트하우스" | "모텔" | "리조트" | "한옥";
	address: string;
	/** WGS84 위도 */
	latitude: number;
	/** WGS84 경도 */
	longitude: number;
	description: string;
	/** 대표 이미지 URL */
	imageUrl: string;
	/** 1박 최저 가격 (원) */
	pricePerNight: number;
	rating: number;
	/** BE 미제공 — 0 기본값 */
	reviewCount: number;
	/** be #43 V16 도입 — list/detail 응답에서 직접 매핑 */
	sido: string;
	/** be #43 V16 도입 — list/detail 응답에서 직접 매핑 */
	gugun: string;
	/** BE 미제공 */
	hostId?: number;
	/** BE 미제공 */
	hostName?: string;
	/** BE 미제공 */
	hostPhone?: string;
	/** BE 미제공 */
	amenities: string[];
	/** BE 미제공 */
	maxGuests: number;
	/** BE 미제공 */
	checkInTime: string;
	/** BE 미제공 */
	checkOutTime: string;
	/** 반경 검색 시에만 존재 */
	distanceMeters?: number | null;
}

/**
 * BE AccommodationResponse → FE Accommodation 매핑.
 * BE가 제공하지 않는 필드는 안전한 기본값으로 채운다.
 */
function mapApiToAccommodation(r: AccommodationApiResponse): Accommodation {
	return {
		id: r.id,
		name: r.name,
		type: TYPE_LABEL[r.type] ?? "호텔",
		address: r.address,
		latitude: r.lat,
		longitude: r.lng,
		description: r.description,
		imageUrl: r.thumbnailUrl,
		pricePerNight: r.priceFrom,
		rating: r.rating,
		reviewCount: 0, // BE 미제공
		sido: r.sido, // be #43
		gugun: r.gugun, // be #43
		amenities: [], // BE 미제공
		maxGuests: 2, // BE 미제공 — 기본값
		checkInTime: "15:00", // BE 미제공 — 기본값
		checkOutTime: "11:00", // BE 미제공 — 기본값
		distanceMeters: r.distanceMeters,
	};
}

export const useAccommodationsStore = defineStore("accommodations", () => {
	const items = ref<Accommodation[]>([]);
	const loading = ref(false);
	const error = ref<string | null>(null);
	const searchQuery = ref("");
	const selectedType = ref("");
	const selectedSido = ref("");
	const selectedGugun = ref("");
	const maxPrice = ref<number | null>(null);
	/**
	 * GET /api/accommodations/regions 캐시 (be #43).
	 * null = 아직 fetch 전. 한 번 채워지면 재호출하지 않는다.
	 */
	const regions = ref<AccommodationRegionsApiResponse | null>(null);

	/** 로드된 items 기준 타입 목록 */
	const types = computed<Accommodation["type"][]>(
		() =>
			[...new Set(items.value.map((a) => a.type))] as Accommodation["type"][],
	);

	/**
	 * 시·도 목록.
	 * regions(BE distinct)가 로드되어 있으면 그것을 우선,
	 * 그렇지 않으면 현재 items에서 파생(빈 문자열 제외).
	 */
	const sidos = computed(() => {
		if (regions.value && regions.value.sidos.length > 0) {
			return regions.value.sidos;
		}
		return [...new Set(items.value.map((a) => a.sido))].filter(Boolean);
	});

	/**
	 * 선택된 시·도에 해당하는 시·군·구 목록.
	 * regions 캐시 우선, 없으면 items 파생, sido 미선택 시 빈 배열.
	 */
	const gugunsForSelectedSido = computed<string[]>(() => {
		if (!selectedSido.value) return [];
		if (regions.value) {
			return regions.value.gugunsBySido[selectedSido.value] ?? [];
		}
		return [
			...new Set(
				items.value
					.filter((a) => a.sido === selectedSido.value)
					.map((a) => a.gugun),
			),
		].filter(Boolean);
	});

	/** 클라이언트 사이드 필터 (BE list 쿼리 파라미터 미지원 → FE에서 처리, fe #50). */
	const filtered = computed(() =>
		items.value.filter((a) => {
			const matchesSearch =
				!searchQuery.value ||
				a.name.includes(searchQuery.value) ||
				a.address.includes(searchQuery.value);
			const matchesType = !selectedType.value || a.type === selectedType.value;
			const matchesSido = !selectedSido.value || a.sido === selectedSido.value;
			const matchesGugun =
				!selectedGugun.value || a.gugun === selectedGugun.value;
			const matchesPrice =
				maxPrice.value === null || a.pricePerNight <= maxPrice.value;
			return (
				matchesSearch &&
				matchesType &&
				matchesSido &&
				matchesGugun &&
				matchesPrice
			);
		}),
	);

	function getById(id: number): Accommodation | undefined {
		return items.value.find((a) => a.id === id);
	}

	/**
	 * 숙박 목록 조회 — GET /api/accommodations
	 *
	 * 기본 최대 100건을 한 번에 로드한다.
	 * 실패 시 error를 세트하고 기존 items를 비운다 (silent fallback 금지).
	 */
	async function fetchAccommodations(
		q?: string,
		lat?: number,
		lng?: number,
		radius = 5000,
	): Promise<void> {
		loading.value = true;
		error.value = null;

		const params = new URLSearchParams();
		if (q) params.set("q", q);
		if (lat !== undefined) params.set("lat", String(lat));
		if (lng !== undefined) params.set("lng", String(lng));
		if (lat !== undefined && lng !== undefined)
			params.set("radius", String(radius));
		params.set("size", "100");

		const path = `/api/accommodations${params.size ? `?${params}` : ""}`;

		if (lat !== undefined && lng !== undefined) {
			// 주변 검색 모드 — 응답이 배열
			const result = await get<AccommodationApiResponse[]>(path);
			loading.value = false;
			if (result.error || !result.data) {
				error.value = result.error ?? "숙박 목록을 불러오지 못했습니다.";
				items.value = [];
				return;
			}
			items.value = result.data.map(mapApiToAccommodation);
		} else {
			// 페이지 모드 — 응답이 PageResponse
			const result = await get<AccommodationPageResponse>(path);
			loading.value = false;
			if (result.error || !result.data) {
				error.value = result.error ?? "숙박 목록을 불러오지 못했습니다.";
				items.value = [];
				return;
			}
			items.value = result.data.content.map(mapApiToAccommodation);
		}
	}

	/**
	 * 숙박 단건 조회 — GET /api/accommodations/{id}
	 *
	 * items 캐시에 없을 때 또는 최신 데이터가 필요할 때 호출.
	 * 실패 시 error를 세트하고 null 반환.
	 */
	async function fetchAccommodationById(
		id: number,
	): Promise<Accommodation | null> {
		loading.value = true;
		error.value = null;

		const result = await get<AccommodationApiResponse>(
			`/api/accommodations/${id}`,
		);
		loading.value = false;

		if (result.error || !result.data) {
			error.value = result.error ?? "숙박 정보를 불러오지 못했습니다.";
			return null;
		}

		const mapped = mapApiToAccommodation(result.data);

		// 캐시 갱신: items에 없으면 추가, 있으면 교체
		const idx = items.value.findIndex((a) => a.id === id);
		if (idx >= 0) {
			items.value.splice(idx, 1, mapped);
		} else {
			items.value.push(mapped);
		}

		return mapped;
	}

	/**
	 * 행정구역 옵션 조회 — GET /api/accommodations/regions (be #43).
	 *
	 * 한 세션에 한 번만 호출(`regions !== null`이면 즉시 return).
	 * 실패 시 error를 세트하지만 throw하지 않는다 (필터 select는 items fallback로 동작).
	 */
	async function fetchRegions(): Promise<void> {
		if (regions.value !== null) return;

		const result = await get<AccommodationRegionsApiResponse>(
			"/api/accommodations/regions",
		);

		if (result.error || !result.data) {
			error.value = result.error ?? "지역 목록을 불러오지 못했습니다.";
			return;
		}

		regions.value = result.data;
	}

	function setSearch(query: string) {
		searchQuery.value = query;
	}
	function setType(type: string) {
		selectedType.value = type;
	}
	/** sido 변경 시 gugun 선택은 자동 초기화 — 잘못된 (sido, gugun) 조합 방지. */
	function setSido(sido: string) {
		if (sido !== selectedSido.value) {
			selectedGugun.value = "";
		}
		selectedSido.value = sido;
	}
	function setGugun(gugun: string) {
		selectedGugun.value = gugun;
	}
	function setMaxPrice(price: number | null) {
		maxPrice.value = price;
	}

	return {
		items,
		loading,
		error,
		searchQuery,
		selectedType,
		selectedSido,
		selectedGugun,
		maxPrice,
		regions,
		types,
		sidos,
		gugunsForSelectedSido,
		filtered,
		getById,
		fetchAccommodations,
		fetchAccommodationById,
		fetchRegions,
		setSearch,
		setType,
		setSido,
		setGugun,
		setMaxPrice,
	};
});
