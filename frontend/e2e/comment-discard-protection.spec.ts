import { expect, test, type Route } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

const fulfill = (route: Route, data: unknown) => route.fulfill({ json: { success: true, data } })

for (const width of [390, 1280]) {
  test(`comment and reply drafts require discard approval at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    await page.route(/\/api\/v1\/posts\/1\/comments(?:\?.*)?$/, route => fulfill(route, {
      content: [{
        commentId: 41, content: 'Original comment', author: mockUser,
        likeCount: 0, isDeleted: false, children: [], mentions: [], replyCount: 0,
        hasReplies: false, createdAt: '2026-01-01T00:00:00',
      }], number: 0, size: 50, totalElements: 1, totalPages: 1, first: true, last: true,
    }))
    await page.goto('/board/general/post/1')
    const row = page.locator('#comment-41')
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    const toggleReply = row.locator('button[type="button"]').filter({ hasText: /^답글$/ })
    await toggleReply.click()
    await row.getByRole('combobox').fill('Unsent reply')
    await toggleReply.click()
    await expect(confirmation).toBeVisible()
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(row.getByRole('combobox')).toHaveValue('Unsent reply')
    await toggleReply.click()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(row.getByRole('combobox')).toHaveCount(0)

    const composer = page.locator('#comment-composer').getByRole('combobox')
    await composer.fill('Unsent top level comment')
    expect(await page.evaluate(() => {
      const event = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(event)
      return event.defaultPrevented
    })).toBe(true)
    await page.getByRole('link', { name: '노비스 홈', exact: true }).click()
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(composer).toHaveValue('Unsent top level comment')
    await expect(page).toHaveURL(/\/board\/general\/post\/1\/?$/)
    await page.getByRole('link', { name: '노비스 홈', exact: true }).click()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(page).toHaveURL('/')
  })
}

test('creating a comment refreshes the post header count for a string route ID', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  let count = 0
  await page.route(/\/api\/v1\/posts\/1(?:\?.*)?$/, route => fulfill(route, {
    postId: 1, title: 'Count regression', contents: '<p>Body</p>', viewCount: 1,
    likeCount: 0, commentCount: count, isLiked: false, isScrapped: false, isNotice: false,
    isNsfw: false, isSpoiler: false, isSecret: false, isDeleted: false,
    createdAt: '2026-01-01T00:00:00', modifiedAt: '2026-01-01T00:00:00',
    board: { boardId: 1, boardName: 'General', boardUrl: 'general' }, author: mockUser,
    tags: [], files: [], poll: null,
  }))
  await page.route(/\/api\/v1\/posts\/1\/comments(?:\?.*)?$/, route => {
    if (route.request().method() === 'POST') {
      count++
      return fulfill(route, { commentId: 41 })
    }
    return fulfill(route, {
      content: [], number: 0, size: 50, totalElements: count, totalPages: 1, first: true, last: true,
    })
  })
  await page.goto('/board/general/post/1')
  const headerCount = page.locator('.nv-post-header span').filter({ has: page.locator('svg.lucide-message-square') })
  await expect(headerCount).toHaveText('0')
  const composer = page.locator('#comment-composer')
  await composer.getByRole('combobox').fill('Count this comment')
  await composer.locator('button[type="submit"]').click()
  await expect(headerCount).toHaveText('1')
  await expect(composer.getByRole('combobox')).toHaveValue('')
})
