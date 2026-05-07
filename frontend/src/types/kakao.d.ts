/**
 * 카카오맵 SDK 최소 타입 선언
 * 전체 타입은 @types/kakao.maps.d.ts 패키지 설치 시 불필요
 */

interface KakaoMapsLatLng {
	getLat(): number;
	getLng(): number;
}

interface KakaoMapsLatLngBounds {
	getSouthWest(): KakaoMapsLatLng;
	getNorthEast(): KakaoMapsLatLng;
}

interface KakaoMapsMap {
	setCenter(latlng: KakaoMapsLatLng): void;
	setLevel(level: number): void;
	getLevel(): number;
	getBounds(): KakaoMapsLatLngBounds;
	relayout(): void;
}

interface KakaoMapsMarker {
	setMap(map: KakaoMapsMap | null): void;
	setImage(image: KakaoMapsMarkerImage): void;
	getPosition(): KakaoMapsLatLng;
	setTitle(title: string): void;
}

type KakaoMapsMarkerImage = Record<string, unknown>;

interface KakaoMapsInfoWindow {
	open(map: KakaoMapsMap, marker: KakaoMapsMarker): void;
	close(): void;
}

type KakaoMapsSize = Record<string, unknown>;

interface KakaoMapsMapsStatic {
	Map: new (
		container: HTMLElement,
		options: {
			center: KakaoMapsLatLng;
			level: number;
		},
	) => KakaoMapsMap;
	LatLng: new (lat: number, lng: number) => KakaoMapsLatLng;
	Marker: new (options: {
		position: KakaoMapsLatLng;
		map?: KakaoMapsMap;
		title?: string;
		image?: KakaoMapsMarkerImage;
	}) => KakaoMapsMarker;
	MarkerImage: new (
		src: string,
		size: KakaoMapsSize,
		options?: object,
	) => KakaoMapsMarkerImage;
	Size: new (width: number, height: number) => KakaoMapsSize;
	InfoWindow: new (options: {
		content: string;
		removable?: boolean;
	}) => KakaoMapsInfoWindow;
	event: {
		addListener(
			target: KakaoMapsMap | KakaoMapsMarker,
			type: string,
			handler: () => void,
		): void;
		removeListener(
			target: KakaoMapsMap | KakaoMapsMarker,
			type: string,
			handler: () => void,
		): void;
	};
}

interface KakaoStatic {
	maps: KakaoMapsMapsStatic & {
		load(callback: () => void): void;
	};
}

declare global {
	interface Window {
		kakao?: KakaoStatic;
	}
}

export type {};
