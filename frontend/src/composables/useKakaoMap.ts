import { onBeforeUnmount, ref, shallowRef } from 'vue'

/** SDK 로드 상태 (모듈 싱글톤) */
let _sdkLoadPromise: Promise<void> | null = null

function loadSdk(appKey: string): Promise<void> {
  if (_sdkLoadPromise) return _sdkLoadPromise

  _sdkLoadPromise = new Promise((resolve, reject) => {
    // 이미 로드된 경우
    if (window.kakao?.maps) {
      window.kakao.maps.load(resolve)
      return
    }

    const script = document.createElement('script')
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services,clusterer`
    script.async = true

    script.onload = () => {
      if (!window.kakao?.maps) {
        reject(new Error('kakao.maps is undefined after SDK load'))
        return
      }
      window.kakao.maps.load(resolve)
    }

    script.onerror = () => {
      _sdkLoadPromise = null // 재시도 가능하도록
      reject(new Error('카카오맵 SDK 로드 실패. 도메인 등록 및 API Key를 확인하세요.'))
    }

    document.head.appendChild(script)
  })

  return _sdkLoadPromise
}

export interface UseKakaoMapOptions {
  center: { lat: number; lng: number }
  level?: number
}

export function useKakaoMap(
  containerRef: Readonly<ReturnType<typeof ref<HTMLElement | null>>>,
  options: UseKakaoMapOptions,
) {
  const map = shallowRef<KakaoMapsMap | null>(null)
  const sdkReady = ref(false)
  const error = ref<string | null>(null)

  const appKey = import.meta.env.VITE_KAKAO_MAP_JS_KEY as string | undefined

  async function initMap() {
    if (!appKey) {
      error.value = 'VITE_KAKAO_MAP_JS_KEY 환경변수가 설정되지 않았습니다.'
      return
    }
    if (!containerRef.value) {
      error.value = '지도 컨테이너 엘리먼트를 찾을 수 없습니다.'
      return
    }

    try {
      await loadSdk(appKey)
      sdkReady.value = true

      const mapsApi = window.kakao?.maps
      if (!mapsApi) {
        error.value = 'kakao.maps API를 사용할 수 없습니다.'
        return
      }
      const { Map: KakaoMapCtor, LatLng } = mapsApi
      const center = new LatLng(options.center.lat, options.center.lng)
      map.value = new KakaoMapCtor(containerRef.value, {
        center,
        level: options.level ?? 7,
      })
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    }
  }

  onBeforeUnmount(() => {
    map.value = null
  })

  return { map, sdkReady, error, initMap }
}
