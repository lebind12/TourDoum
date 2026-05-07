/**
 * fetch 래퍼.
 * VITE_API_BASE_URL 환경 변수가 없으면 http://localhost:8080 으로 폴백.
 * 모든 요청에 credentials: 'include' 기본 적용 (세션 쿠키 송수신).
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface ApiResponse<T> {
  data: T | null
  error: string | null
}

/** 4xx/5xx 응답 본문에서 메시지를 추출한다. */
async function extractError(res: Response): Promise<string> {
  try {
    const body = await res.json()
    return body?.message ?? `HTTP ${res.status}`
  } catch {
    return `HTTP ${res.status}`
  }
}

export async function get<T>(path: string): Promise<ApiResponse<T>> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      credentials: 'include',
    })
    if (!res.ok) {
      return { data: null, error: await extractError(res) }
    }
    const data: T = await res.json()
    return { data, error: null }
  } catch (err) {
    return {
      data: null,
      error: err instanceof Error ? err.message : '알 수 없는 오류',
    }
  }
}

export async function post<T>(path: string, body: unknown): Promise<ApiResponse<T>> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      return { data: null, error: await extractError(res) }
    }
    // 204 No Content 등 빈 응답 처리
    const text = await res.text()
    const data: T = text ? (JSON.parse(text) as T) : (null as T)
    return { data, error: null }
  } catch (err) {
    return {
      data: null,
      error: err instanceof Error ? err.message : '알 수 없는 오류',
    }
  }
}
