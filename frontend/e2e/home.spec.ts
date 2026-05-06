// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
// 주의: 이 e2e 테스트는 백엔드 없이도 Vite dev server만으로 실행 가능.
//       백엔드가 없으면 "백엔드 미가동" 메시지가 표시되며, 타이틀 확인만 수행한다.
import { expect, test } from '@playwright/test'

test('홈 페이지에 "TourDoum" 타이틀이 표시된다', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('h1')).toContainText('TourDoum')
})
