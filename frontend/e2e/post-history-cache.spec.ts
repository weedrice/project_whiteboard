import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  for (const outcome of ['success', 'failure'] as const) {
    test(`post history survives a pending bookmark and ${outcome} at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await installMockApi(page, { authenticated: true })

      let scrapped = false
      let bookmarkRequests = 0
      let versionRequests = 0
      let releaseBookmark!: () => void
      const pendingBookmark = new Promise<void>(resolve => { releaseBookmark = resolve })
      const originalVersion = {
        versionId: 11, actionType: 'MODIFY', title: '보존할 수정 이력', createdAt: '2026-01-01T00:00:00',
      }

      await page.route(/\/api\/v1\/posts\/1(?:\?.*)?$/, route => route.fulfill({ json: {
        success: true,
        data: {
          postId: 1, title: 'History cache regression', contents: '<p>Post body</p>',
          viewCount: 1, likeCount: 0, liked: false, scrapped, commentCount: 0, editCount: 1,
          isNotice: false, isNsfw: false, isSpoiler: false, isSecret: false, isDeleted: false,
          createdAt: '2026-01-01T00:00:00', modifiedAt: '2026-01-02T00:00:00',
          board: { boardId: 1, boardName: 'General', boardUrl: 'general' },
          author: mockUser, tags: [], files: [], poll: null,
        },
      } }))
      await page.route('**/api/v1/posts/1/versions', async route => {
        versionRequests += 1
        await route.fulfill({ json: {
          success: true,
          data: [originalVersion],
        } })
      })
      await page.route('**/api/v1/posts/1/scrap', async route => {
        bookmarkRequests += 1
        await pendingBookmark
        scrapped = outcome === 'success'
        await route.fulfill(outcome === 'success'
          ? { json: { success: true, data: null } }
          : { status: 400, json: { success: false, data: null, error: { code: 'C001', message: 'Bookmark rejected' } } })
      })

      try {
        await page.goto('/board/general/post/1')
        const openHistory = page.getByRole('button', { name: '게시글 수정 이력', exact: true })
        const history = page.getByRole('dialog', { name: '게시글 수정 이력', exact: true })
        const bookmark = page.locator('.nv-post-reaction-row').getByRole('button', { name: '북마크', exact: true })

        await openHistory.click()
        await expect(history.getByText(originalVersion.title, { exact: true })).toBeVisible()
        await history.getByRole('button', { name: '닫기', exact: true }).click()
        await expect(history).not.toBeVisible()

        await bookmark.click()
        await expect.poll(() => bookmarkRequests).toBe(1)
        await expect(bookmark).toHaveAttribute('aria-pressed', 'true')
        await openHistory.click()
        // No new server response can repair a corrupted cache during this assertion.
        await expect(history.getByText(originalVersion.title, { exact: true })).toBeVisible()
        await expect(history.getByText('표시할 수정 이력이 없습니다.', { exact: true })).not.toBeVisible()

        const detailRefetched = page.waitForResponse(response => (
          new URL(response.url()).pathname === '/api/v1/posts/1'
          && response.request().method() === 'GET'
        ))
        releaseBookmark()
        await (await detailRefetched).finished()
        await expect(history.getByText(originalVersion.title, { exact: true })).toBeVisible()
        await expect(history.getByTestId('post-version-timeline').locator('li')).toHaveCount(1)
        expect(versionRequests).toBe(1)
        await history.getByRole('button', { name: '닫기', exact: true }).click()
        await expect(bookmark).toHaveAttribute('aria-pressed', String(outcome === 'success'))
      } finally {
        releaseBookmark()
      }
    })
  }
}
