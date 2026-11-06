import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const viewport of [
  { name: 'desktop', width: 1280, height: 800 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  for (const authenticated of [true, false]) {
    test(`withdrawn post author exposes only available actions (${viewport.name}, ${authenticated ? 'member' : 'guest'})`, async ({ page }) => {
      await page.setViewportSize(viewport)
      const author = { userId: 8, loginId: '', displayName: '탈퇴한 사용자', profileImageUrl: null, withdrawn: true }
      await installMockApi(page, { authenticated, postAuthor: author })
      await page.goto('/board/general/post/1')
      const trigger = page.getByRole('button', { name: '탈퇴한 사용자', exact: true })
      await expect(trigger).toBeVisible()
      if (authenticated) {
        await trigger.click()
        await expect(page.getByRole('menuitem')).toHaveCount(1)
        await expect(page.getByRole('menuitem', { name: '차단하기', exact: true })).toBeVisible()
        await page.keyboard.press('Escape')
        await expect(trigger).toBeFocused()
      } else {
        await expect(trigger).toBeDisabled()
      }
      await expect.poll(() => page.locator('script[type="application/ld+json"]').evaluateAll((scripts) => {
        const article = scripts.map((script) => JSON.parse(script.textContent || '{}'))
          .find((data) => data['@type'] === 'Article')
        return article?.author?.name
      })).toBe('탈퇴한 사용자')
    })
  }
}
