<script setup lang="ts">
import { useKakaoMap } from "@/composables/useKakaoMap";
import { onMounted, ref, watch } from "vue";

export interface MapCenter {
	lat: number;
	lng: number;
}

export interface MapMarker {
	id: number;
	lat: number;
	lng: number;
	title: string;
}

export interface MapBounds {
	sw: MapCenter;
	ne: MapCenter;
}

const props = withDefaults(
	defineProps<{
		center: MapCenter;
		level?: number;
		markers?: MapMarker[];
		highlightedMarkerId?: number | null;
	}>(),
	{
		level: 7,
		markers: () => [],
		highlightedMarkerId: null,
	},
);

const emit = defineEmits<{
	"select-marker": [id: number];
	"bounds-changed": [bounds: MapBounds];
}>();

const containerRef = ref<HTMLElement | null>(null);
const { map, sdkReady, error, initMap } = useKakaoMap(containerRef, {
	center: props.center,
	level: props.level,
});

/** 열려 있는 InfoWindow (한 번에 하나만) */
let activeInfoWindow: KakaoMapsInfoWindow | null = null;
/** marker 인스턴스 캐시 */
const markerInstances: Map<number, KakaoMapsMarker> = new Map();

function closeActiveInfoWindow() {
	if (activeInfoWindow) {
		activeInfoWindow.close();
		activeInfoWindow = null;
	}
}

function renderMarkers() {
	if (!map.value || !window.kakao?.maps) return;

	const { LatLng, Marker, InfoWindow, event } = window.kakao.maps;

	// 이전 마커 제거
	for (const m of markerInstances.values()) {
		m.setMap(null);
	}
	markerInstances.clear();
	closeActiveInfoWindow();

	for (const markerData of props.markers) {
		const position = new LatLng(markerData.lat, markerData.lng);
		const marker = new Marker({
			position,
			map: map.value,
			title: markerData.title,
		});

		const infoWindow = new InfoWindow({
			content: `<div style="padding:4px 8px;font-size:13px;white-space:nowrap">${markerData.title}</div>`,
			removable: true,
		});

		event.addListener(marker, "click", () => {
			closeActiveInfoWindow();
			if (map.value) {
				infoWindow.open(map.value, marker);
				activeInfoWindow = infoWindow;
			}
			emit("select-marker", markerData.id);
		});

		markerInstances.set(markerData.id, marker);
	}

	// bounds-changed 이벤트 등록
	event.addListener(map.value, "bounds_changed", () => {
		if (!map.value) return;
		const bounds = map.value.getBounds();
		const sw = bounds.getSouthWest();
		const ne = bounds.getNorthEast();
		emit("bounds-changed", {
			sw: { lat: sw.getLat(), lng: sw.getLng() },
			ne: { lat: ne.getLat(), lng: ne.getLng() },
		});
	});
}

function highlightMarker(id: number | null) {
	// 단순 구현: 특별한 시각 변화 없이 해당 마커의 InfoWindow 열기
	if (id === null) {
		closeActiveInfoWindow();
		return;
	}
	// 향후 마커 이미지 교체로 강조 가능
}

onMounted(async () => {
	await initMap();
	if (sdkReady.value && map.value) {
		renderMarkers();
	}
});

watch(
	() => props.markers,
	() => {
		if (sdkReady.value && map.value) {
			renderMarkers();
		}
	},
	{ deep: true },
);

watch(
	() => props.center,
	(newCenter) => {
		if (!map.value || !window.kakao?.maps) return;
		const { LatLng } = window.kakao.maps;
		map.value.setCenter(new LatLng(newCenter.lat, newCenter.lng));
	},
);

watch(
	() => props.highlightedMarkerId,
	(id) => {
		highlightMarker(id ?? null);
	},
);
</script>

<template>
  <div class="relative w-full h-full min-h-[256px]">
    <!-- 지도 컨테이너 -->
    <div ref="containerRef" class="w-full h-full" />

    <!-- 로딩 오버레이 -->
    <div
      v-if="!sdkReady && !error"
      class="absolute inset-0 flex items-center justify-center bg-muted"
    >
      <p class="text-muted-foreground text-sm animate-pulse">지도 로딩 중…</p>
    </div>

    <!-- 에러 상태 -->
    <div
      v-if="error"
      class="absolute inset-0 flex flex-col items-center justify-center bg-muted gap-2"
    >
      <p class="text-destructive text-sm font-medium">지도 로드 실패</p>
      <p class="text-muted-foreground text-xs max-w-xs text-center">{{ error }}</p>
    </div>
  </div>
</template>
