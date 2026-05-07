import { mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createPinia, setActivePinia } from 'pinia'
// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import HomeView from '../HomeView.vue'

// MSW 서버 설정 — /api/health 엔드포인트를 모킹
const server = setupServer(
  http.get('http://localhost:8080/api/health', () => {
    return HttpResponse.json({ status: 'UP' })
  }),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('HomeView', () => {
  it('마운트 후 헬스체크 결과 "UP"을 표시한다', async () => {
    setActivePinia(createPinia())

    const wrapper = mount(HomeView, {
      global: {
        plugins: [createPinia()],
      },
    })

    // fetchHealth()가 비동기이므로 잠시 대기
    await new Promise((r) => setTimeout(r, 50))
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('UP')
  })

  it('API 오류 시 "백엔드 미가동" 메시지를 표시한다', async () => {
    setActivePinia(createPinia())

    // 오류 응답으로 핸들러 교체
    server.use(
      http.get('http://localhost:8080/api/health', () => {
        return HttpResponse.error()
      }),
    )

    const wrapper = mount(HomeView, {
      global: {
        plugins: [createPinia()],
      },
    })

    await new Promise((r) => setTimeout(r, 50))
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('백엔드 미가동')
  })
})
