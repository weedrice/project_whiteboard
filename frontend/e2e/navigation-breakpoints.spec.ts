import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

for (const authenticated of [false, true]) {
  for (const width of [390, 639, 640, 700, 767, 768, 1280]) {
    test(`space navigation remains available at ${width}px (${authenticated ? 'member' : 'guest'})`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await installMockApi(page, { authenticated })
      if (authenticated) {
        await page.route('**/api/v1/users/me', (route) => route.fulfill({ json: {
          success: true, data: { ...mockUser, displayName: '스페이스를둘러보는긴이름사용자' },
        } }))
      }
      await page.goto('/board/general/post/1')
      await expect(page.getByRole('heading', { name: 'Accessible post' })).toBeVisible()

      const bottomNav = page.locator('.nv-mobile-nav')
      const desktopSpaces = page.locator('#all-board-dropdown-trigger')
      if (width < 768) {
        await expect(bottomNav.getByRole('link', { name: '스페이스', exact: true })).toBeVisible()
        await expect(desktopSpaces).toBeHidden()
        const quickActions = await page.locator('.nv-post-mobile-actions').boundingBox()
        const bottomBox = await bottomNav.boundingBox()
        expect(quickActions).not.toBeNull()
        expect(bottomBox).not.toBeNull()
        expect(quickActions!.y + quickActions!.height).toBeLessThanOrEqual(bottomBox!.y + 1)
        await bottomNav.getByRole('link', { name: '스페이스', exact: true }).click()
        await expect(page).toHaveURL(/\/boards\/?$/)
      } else {
        await expect(bottomNav).toBeHidden()
        await expect(desktopSpaces).toBeVisible()
        await desktopSpaces.click()
        await expect(page.getByRole('menuitem', { name: /General/ })).toBeVisible()
      }
      expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false)
    })
  }
}
