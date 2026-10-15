import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

const viewports = [
  { name: 'desktop', size: { width: 1280, height: 800 } },
  { name: 'mobile', size: { width: 390, height: 844 } },
] as const

for (const viewport of viewports) {
  test(`comment user menu preserves scroll and keyboard focus (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page, { authenticated: true })
    await page.route('**/api/v1/posts/1/comments**', async (route) => {
      const isBestComments = new URL(route.request().url()).pathname.endsWith('/best')
      const comments = Array.from({ length: 20 }, (_, index) => ({
        commentId: index + 100,
        content: `Comment ${index}`,
        createdAt: '2026-10-01T09:00:00',
        author: { userId: 8, displayName: 'Comment Author', profileImageUrl: null },
        likeCount: 0,
        replyCount: 0,
        isDeleted: false,
        isBlinded: false,
        liked: false,
      }))
      await route.fulfill({
        json: {
          success: true,
          data: isBestComments ? [] : {
            content: comments,
            page: 0,
            size: 20,
            totalElements: 20,
            totalPages: 1,
            first: true,
            last: true,
          },
        },
      })
    })

    await page.goto('/board/general/post/1')
    // Keep enough content below the trigger to expose an unpositioned body teleport.
    const trigger = page.locator('#comment-100 .nv-user-menu-button')
    await expect(trigger).toBeVisible()
    await trigger.evaluate((element) => element.scrollIntoView({ block: 'center', behavior: 'instant' }))
    const scrollBefore = await page.evaluate(() => window.scrollY)
    const menu = page.getByRole('menu')
    const items = menu.getByRole('menuitem')
    const expectScrollPreserved = async () => {
      expect(Math.abs(await page.evaluate(() => window.scrollY) - scrollBefore)).toBeLessThanOrEqual(1)
    }

    for (const openWithKeyboard of [false, true]) {
      if (openWithKeyboard) await trigger.press('ArrowDown')
      else await trigger.click()

      await expect(items.first()).toBeFocused()
      await expect(menu).toBeInViewport()
      await expectScrollPreserved()

      await page.keyboard.press('ArrowDown')
      await expect(items.nth(1)).toBeFocused()
      await page.keyboard.press('End')
      await expect(items.last()).toBeFocused()
      await page.keyboard.press('Home')
      await expect(items.first()).toBeFocused()
      await expectScrollPreserved()

      await page.keyboard.press('Escape')
      await expect(menu).toHaveCount(0)
      await expect(trigger).toBeFocused()
      await expectScrollPreserved()
    }
  })
}
