/**
 * e2e — 도메인횡단 인증 및 라우트 가드 검증
 *
 * 실행 조건: 환경변수 E2E_BACKEND=1 일 때만 실행.
 * BE가 http://localhost:8080 에서 가동 중이어야 한다.
 *
 *   E2E_BACKEND=1 npx playwright test e2e/auth-flow.spec.ts
 *
 * Scenario A: 회원가입 → 자동 로그인 → 홈 닉네임 → /me → /attractions → /favorites → 로그아웃
 * Scenario B: 비로그인 /favorites → /login 리다이렉트
 * Scenario C: 비로그인 /me → /login 리다이렉트
 */
import { expect, test } from '@playwright/test'

const RUN = process.env.E2E_BACKEND === '1'

test.skip(!RUN, 'E2E_BACKEND=1 환경변수 없음 — 실제 BE+FE 필요. 스킵.')

test.describe('도메인횡단 인증 및 가드 검증', () => {
  test('Scenario A: signup → 자동 login → home 닉네임 표시 → /me → /attractions → /favorites → logout', async ({
    page,
  }) => {
    const ts = Date.now()
    const email = `qa-${ts}@example.com`
    const password = 'password1'
    const nickname = `qa${ts}`

    await page.goto('/signup')
    await page.getByLabel(/이메일|email/i).fill(email)
    await page.getByLabel(/비밀번호|password/i).fill(password)
    await page.getByLabel(/닉네임|nickname/i).fill(nickname)
    await page.getByRole('button', { name: /가입|회원가입|sign up/i }).click()

    await expect(page).toHaveURL('/')
    await expect(page.getByText(new RegExp(`.*${nickname}.*`))).toBeVisible()

    await page.goto('/me')
    await expect(page).toHaveURL('/me')
    await expect(page.locator('h1, h2, body')).toContainText(/본인|프로필|계정|profile|account|me/i)

    await page.goto('/attractions')
    await expect(page).toHaveURL('/attractions')
    await expect(page.locator('h1, h2, body')).toContainText(/관광지|attraction|여행/i)

    await page.goto('/favorites')
    await expect(page).toHaveURL('/favorites')
    await expect(page.locator('h1, h2, body')).toContainText(/즐겨찾기|favorite/i)

    await page.getByRole('button', { name: /로그아웃|logout/i }).click()

    await expect(page).toHaveURL('/')
    await expect(page.getByRole('link', { name: /로그인|login/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /회원가입|sign up/i })).toBeVisible()
  })

  test('Scenario B: 비로그인 /favorites 접근 → /login 리다이렉트 확인', async ({ page }) => {
    await page.goto('/favorites')

    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByLabel(/이메일|email/i)).toBeVisible()
    await expect(page.getByLabel(/비밀번호|password/i)).toBeVisible()
  })

  test('Scenario C: 비로그인 /me 접근 → /login 리다이렉트 확인', async ({ page }) => {
    await page.goto('/me')

    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByLabel(/이메일|email/i)).toBeVisible()
    await expect(page.getByLabel(/비밀번호|password/i)).toBeVisible()
  })
})
