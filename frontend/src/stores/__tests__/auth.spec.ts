/**
 * auth.spec.ts — BE 로그인 응답 계약 검증
 *
 * BE 확정 계약 (POST /api/auth/login 200):
 * { "id": number, "email": string, "nickname": string, "role": "ROLE_USER" }
 */
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createPinia, setActivePinia } from 'pinia'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../auth'

// BE 계약 응답 픽스처
const LOGIN_SUCCESS_RESPONSE = {
  id: 42,
  email: 'user@example.com',
  nickname: '여행자',
  role: 'ROLE_USER',
}

const server = setupServer(
  http.post('http://localhost:8080/api/auth/login', () => {
    return HttpResponse.json(LOGIN_SUCCESS_RESPONSE)
  }),
  http.get('http://localhost:8080/api/me', () => {
    return HttpResponse.json(LOGIN_SUCCESS_RESPONSE)
  }),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('useAuthStore — login()', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('로그인 성공 시 currentUser에 BE 계약 필드가 모두 설정된다', async () => {
    const store = useAuthStore()

    const result = await store.login({
      email: 'user@example.com',
      password: 'pass123',
    })

    expect(result).toBe(true)
    expect(store.currentUser).not.toBeNull()
    expect(store.currentUser?.id).toBe(42)
    expect(store.currentUser?.email).toBe('user@example.com')
    expect(store.currentUser?.nickname).toBe('여행자')
    // BE 확정: "ROLE_USER" (not "USER")
    expect(store.currentUser?.role).toBe('ROLE_USER')
  })

  it('로그인 성공 시 error는 null이다', async () => {
    const store = useAuthStore()
    await store.login({ email: 'user@example.com', password: 'pass123' })
    expect(store.error).toBeNull()
  })

  it('로그인 성공 시 loading은 false로 복귀한다', async () => {
    const store = useAuthStore()
    await store.login({ email: 'user@example.com', password: 'pass123' })
    expect(store.loading).toBe(false)
  })

  it('4xx 오류 시 currentUser는 null이고 error가 설정된다', async () => {
    server.use(
      http.post('http://localhost:8080/api/auth/login', () => {
        return HttpResponse.json(
          { message: '이메일 또는 비밀번호가 올바르지 않습니다.' },
          { status: 401 },
        )
      }),
    )

    const store = useAuthStore()
    const result = await store.login({
      email: 'wrong@example.com',
      password: 'wrong',
    })

    expect(result).toBe(false)
    expect(store.currentUser).toBeNull()
    expect(store.error).toBeTruthy()
  })

  it('204 No Content(빈 응답) 시 /api/me 폴백으로 currentUser가 설정된다', async () => {
    server.use(
      http.post('http://localhost:8080/api/auth/login', () => {
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const store = useAuthStore()
    const result = await store.login({
      email: 'user@example.com',
      password: 'pass123',
    })

    expect(result).toBe(true)
    expect(store.currentUser?.role).toBe('ROLE_USER')
  })
})

describe('useAuthStore — signup() → login() 연쇄', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    server.use(
      http.post('http://localhost:8080/api/members/signup', () => {
        return HttpResponse.json(LOGIN_SUCCESS_RESPONSE)
      }),
    )
  })

  it('회원가입 성공 시 자동 로그인으로 currentUser가 설정된다', async () => {
    const store = useAuthStore()
    const result = await store.signup({
      email: 'user@example.com',
      password: 'pass123',
      nickname: '여행자',
    })

    expect(result).toBe(true)
    expect(store.currentUser?.role).toBe('ROLE_USER')
  })
})
