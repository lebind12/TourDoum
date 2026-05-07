import { defineConfig, devices } from '@playwright/test'

/**
 * e2e 테스트 설정.
 *
 * 개발용 dev 서버(5173)와 충돌하지 않도록 e2e 전용 포트(5174)에서 vite를 별도로 띄운다.
 * 개발자가 `npm run dev`를 띄워둔 채로도 `npm run test:e2e`를 돌릴 수 있다.
 * --strictPort: 5174가 점유 중이면 즉시 실패(다른 포트로 자동 폴백 X)해서 충돌을 즉시 인지.
 *
 * CI(Jenkins)에서는 PLAYWRIGHT_BASE_URL 환경 변수로 URL 오버라이드 가능.
 */
// BE SecurityConfig.allowedOrigins는 http://localhost:5173,5174만 허용한다.
// 127.0.0.1로 띄우면 브라우저 origin이 http://127.0.0.1:5174가 되어 CORS 차단된다.
// → 반드시 localhost로 통일 (TASK_18 진단 결과).
const E2E_HOST = 'localhost'
const E2E_PORT = 5174
const E2E_URL = `http://${E2E_HOST}:${E2E_PORT}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? E2E_URL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: `npm run dev -- --host ${E2E_HOST} --port ${E2E_PORT} --strictPort`,
    url: E2E_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
})
