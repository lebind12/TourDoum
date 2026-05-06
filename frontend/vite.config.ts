import { URL, fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // e2e 디렉터리는 Playwright 전용 — Vitest에서 제외
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
  },
})
