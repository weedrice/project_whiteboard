import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [1280, 390]) {
  test(`personal feed card body and controls navigate independently (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    await page.route('**/api/v1/users/me/feeds**', (route) => route.fulfill({
      json: {
        success: true,
        data: {
          content: [{
            feedId: 1,
            contentType: 'POST',
            post: {
              postId: 1,
              title: 'Personal post',
              contentsExcerpt: '<p>Personal feed body</p>',
              boardUrl: 'general',
              boardName: 'General',
              author: { userId: 7, displayName: 'Tester' },
              createdAt: '2026-10-01T09:00:00',
              likeCount: 2,
              commentCount: 3,
              viewCount: 5,
              firstMediaType: 'video',
              firstMediaUrl: 'https://www.youtube.com/watch?v=video-id',
            },
          }],
          number: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
        },
      },
    }))
    await page.route('https://www.youtube-nocookie.com/**', (route) => route.fulfill({
      contentType: 'text/html', body: '<p>Video preview</p>',
    }))

    const card = page.locator('.nv-home-card-personal')
    for (const selector of ['.nv-home-card-title', '.nv-home-card-body', '.nv-home-card-personal-footer', null]) {
      await page.goto('/?view=feed')
      await expect(card).toBeVisible()
      await card.scrollIntoViewIfNeeded()
      // Use real pointer coordinates so the test exercises the overlay and stacking order.
      const target = selector ? card.locator(selector) : card
      const box = await target.boundingBox()
      expect(box).not.toBeNull()
      await page.mouse.click(box!.x + (selector ? box!.width / 2 : 8), box!.y + (selector ? box!.height / 2 : box!.height - 8))
      await expect(page).toHaveURL(/\/board\/general\/post\/1\//)
    }

    await page.goto('/?view=feed')
    await card.locator('.nv-home-card-board-link').click()
    await expect(page).toHaveURL(/\/board\/general\/$/)

    await page.goto('/?view=feed')
    await card.locator('.nv-home-card-interactive button').click()
    await expect(card.locator('iframe')).toBeVisible()
    await expect(page).toHaveURL(/\?view=feed$/)

    const postLink = card.locator('.nv-home-card-hit-area')
    await postLink.focus()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/board\/general\/post\/1\//)
  })
}
