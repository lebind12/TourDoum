import { defineConfig, devices } from "@playwright/test";

/**
 * e2e 테스트 설정.
 * 백엔드가 미가동 상태에서는 webServer의 vite dev server만 실행됨.
 * health check 응답은 "백엔드 미가동" 메시지로 폴백하므로 e2e 기본 시나리오는 통과 가능.
 *
 * CI(Jenkins) 환경에서는 PLAYWRIGHT_BASE_URL 환경 변수로 URL 오버라이드 가능.
 */
export default defineConfig({
	testDir: "./e2e",
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	retries: process.env.CI ? 2 : 0,
	workers: process.env.CI ? 1 : undefined,
	reporter: "html",
	use: {
		baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:5173",
		trace: "on-first-retry",
	},
	projects: [
		{
			name: "chromium",
			use: { ...devices["Desktop Chrome"] },
		},
	],
	webServer: {
		command: "npm run dev",
		url: "http://localhost:5173",
		reuseExistingServer: !process.env.CI,
		timeout: 120 * 1000,
	},
});
