import { expect, test, type Route } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

function fulfillJson(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, error: null }),
  })
}

test('comment refetch updates an existing author name and photo without losing an open reply', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const original = {
    commentId: 41,
    content: 'Existing comment with unchanged content and count',
    author: { userId: 42, displayName: 'Before Rename', profileImageUrl: '/author-before.png' },
    likeCount: 0, isDeleted: false, children: [], mentions: [], replyCount: 0, hasReplies: false,
    createdAt: '2026-01-01T00:00:00',
  }
  let author = original.author
  let created = false
  let commentReads = 0
  const writes: unknown[] = []
  await page.route('**/author-*.png', route => route.fulfill({
    status: 200,
    contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
  }))
  await page.route(/\/api\/v1\/posts\/1\/comments(?:\?.*)?$/, (route) => {
    if (route.request().method() === 'POST') {
      writes.push(route.request().postDataJSON())
      created = true
      return fulfillJson(route, { commentId: 43 })
    }
    commentReads += 1
    const content = [
      { ...original, author },
      ...(created ? [{ ...original, commentId: 43, content: 'A new comment triggers refetch', author: mockUser }] : []),
    ]
    return fulfillJson(route, {
      content, number: 0, size: 50, totalElements: content.length, totalPages: 1, first: true, last: true,
    })
  })

  await page.goto('/board/general/post/1')
  const row = page.locator('#comment-41')
  await expect(row.getByText('Before Rename', { exact: true })).toBeVisible()
  await expect(row.locator('img')).toHaveAttribute('src', '/author-before.png')
  await row.getByRole('button', { name: '답글', exact: true }).click()
  await row.getByRole('combobox').fill('Keep my unfinished reply')
  const initialReads = commentReads

  // The other author's profile changes while this post remains mounted.
  author = { ...author, displayName: 'After Rename', profileImageUrl: '/author-after.png' }
  const composer = page.locator('#comment-composer')
  await composer.getByRole('combobox').fill('A new comment triggers refetch')
  await composer.locator('button[type="submit"]').click()

  await expect.poll(() => commentReads).toBeGreaterThan(initialReads)
  await expect(row.getByText('After Rename', { exact: true })).toBeVisible()
  await expect(row.getByText('Before Rename', { exact: true })).toHaveCount(0)
  await expect(row.locator('img')).toHaveAttribute('src', '/author-after.png')
  await expect(row.getByRole('combobox')).toHaveValue('Keep my unfinished reply')
  expect(writes).toEqual([{ content: 'A new comment triggers refetch', parentId: null }])
})
