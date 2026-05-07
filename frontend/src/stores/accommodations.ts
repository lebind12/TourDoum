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
	lat: number;
	lng: number;
	priceFrom: number;
	rating: number;
	thumbnailUrl: string;
	description: string;
	distanceMeters: number | null;
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
	/** BE 미제공 — address에서 파생 또는 빈 문자열 */
	sido: string;
	/** BE 미제공 — 빈 문자열 */
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
		sido: "", // BE 미제공
		gugun: "", // BE 미제공
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
	const maxPrice = ref<number | null>(null);

	/** 로드된 items 기준 타입 목록 */
	const types = computed<Accommodation["type"][]>(
		() =>
			[...new Set(items.value.map((a) => a.type))] as Accommodation["type"][],
	);

	/** 로드된 items 기준 시도 목록 (sido 빈 문자열 제외) */
	const sidos = computed(() =>
		[...new Set(items.value.map((a) => a.sido))].filter(Boolean),
	);

	/** 클라이언트 사이드 필터 (type/sido/price는 BE API가 미지원 → FE에서 처리) */
	const filtered = computed(() =>
		items.value.filter((a) => {
			const matchesSearch =
				!searchQuery.value ||
				a.name.includes(searchQuery.value) ||
				a.address.includes(searchQuery.value);
			const matchesType = !selectedType.value || a.type === selectedType.value;
			const matchesSido = !selectedSido.value || a.sido === selectedSido.value;
			const matchesPrice =
				maxPrice.value === null || a.pricePerNight <= maxPrice.value;
			return matchesSearch && matchesType && matchesSido && matchesPrice;
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

	function setSearch(query: string) {
		searchQuery.value = query;
	}
	function setType(type: string) {
		selectedType.value = type;
	}
	function setSido(sido: string) {
		selectedSido.value = sido;
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
		maxPrice,
		types,
		sidos,
		filtered,
		getById,
		fetchAccommodations,
		fetchAccommodationById,
		setSearch,
		setType,
		setSido,
		setMaxPrice,
	};
});
