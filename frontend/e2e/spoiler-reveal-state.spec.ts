import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [1280]) {
  test(`revealed spoiler stays visible across reaction updates at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    let liked = false
    let getCount = 0
    let releaseLike!: () => void
    const pendingLike = new Promise<void>(resolve => { releaseLike = resolve })
    await page.route('**/api/v1/posts/1?*', async route => {
      getCount += 1
      await route.fulfill({ json: { success: true, data: {
        postId: 1, title: 'Spoiler reveal regression', contents: '<p>Revealed spoiler body</p>',
        viewCount: 1, likeCount: liked ? 1 : 0, liked, scrapped: false, commentCount: 0,
        isNotice: false, isNsfw: false, isSpoiler: true, isSecret: false, isDeleted: false,
        createdAt: '2026-01-01T00:00:00', modifiedAt: '2026-01-01T00:00:00',
        board: { boardId: 1, boardName: 'General', boardUrl: 'general' },
        author: { userId: 7, displayName: 'Tester', profileImageUrl: null },
        tags: [], files: [], poll: null,
      } } })
    })
    await page.route('**/api/v1/posts/1/like', async route => {
      await pendingLike
      liked = true
      await route.fulfill({ json: { success: true, data: 1 } })
    })
    await page.goto('/board/general/post/1')
    const overlay = page.locator('.nv-post-spoiler')
    await expect(overlay).toBeVisible()
    await overlay.getByRole('button').click()
    await expect(overlay).not.toBeVisible()
    const initialGets = getCount
    await page.getByRole('button', { name: /^추천 \d+$/ }).click()
    try {
      await expect(page.getByRole('button', { name: '추천 1', exact: true })).toBeVisible()
      await expect(overlay).not.toBeVisible()
    } finally {
      releaseLike()
    }
    await expect.poll(() => getCount).toBeGreaterThan(initialGets)
    await expect(page.getByRole('button', { name: '추천 1', exact: true })).toBeEnabled()
    await expect(overlay).not.toBeVisible()
    await expect(page.getByText('Revealed spoiler body', { exact: true })).toBeVisible()
  })
}
