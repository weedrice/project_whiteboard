import { expect, test, type Page, type Route } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

const comment = (commentId: number, hasReplies = false) => ({
  commentId, content: `Comment ${commentId}`,
  author: { userId: 42, displayName: 'Other Author', profileImageUrl: null },
  likeCount: 0, isDeleted: false, children: [], mentions: [],
  replyCount: hasReplies ? 51 : 0, hasReplies, createdAt: '2026-01-01T00:00:00',
})
const json = (route: Route, data: unknown) => route.fulfill({ json: { success: true, data } })
const pageData = (items: ReturnType<typeof comment>[], number = 0) => ({
  content: items.slice(number * 50, (number + 1) * 50), number, size: 50,
  totalElements: items.length, totalPages: Math.max(1, Math.ceil(items.length / 50)),
  first: number === 0, last: (number + 1) * 50 >= items.length,
})

async function mockEmoticon(page: Page) {
  const pack = { emoticonId: 101, name: 'Test Pack', tags: [], isActive: true, createdAt: '2026-01-01', modifiedAt: '2026-01-01', images: [] }
  for (const endpoint of ['purchased', 'my']) {
    await page.route(`**/api/v1/emoticons/${endpoint}?*`, route => json(route, {
      content: endpoint === 'purchased' ? [pack] : [], page: 0, size: 100,
      totalElements: endpoint === 'purchased' ? 1 : 0, totalPages: 1, hasNext: false,
    }))
  }
  await page.route('**/api/v1/emoticons/101', route => json(route, {
    ...pack, images: [{ imageId: 1010, emoticonId: 101, imageUrl: '/uploads/test-sticker.png', sortOrder: 0 }],
  }))
}

for (const width of [390, 1280]) {
  test.describe(`comment drafts at ${width}px`, () => {
    test.use({ viewport: { width, height: 844 } })

    for (const reply of [false, true]) {
      test(`instant emoticon preserves ${reply ? 'reply' : 'comment'} text, mention and focus`, async ({ page }) => {
        await installMockApi(page, { authenticated: true })
        await mockEmoticon(page)
        const writes: unknown[] = []
        await page.route(/\/api\/v1\/posts\/1\/comments(?:\?.*)?$/, route => {
          if (route.request().method() === 'POST') {
            writes.push(route.request().postDataJSON())
            return json(route, { commentId: 44 })
          }
          return json(route, pageData([comment(41)]))
        })
        await page.route('**/api/v1/comments/41/replies?*', route => json(route, pageData([])))
        await page.route('**/api/v1/users/mention-candidates?*', route => json(route, [
          { userId: 7, displayName: 'Alice', profileImageUrl: null },
        ]))
        await page.goto('/board/general/post/1')
        const composer = page.locator(reply ? '#comment-41' : '#comment-composer')
        if (reply) await composer.getByRole('button', { name: '답글', exact: true }).click()
        const textarea = composer.getByRole('combobox')
        await textarea.fill('Keep this @Al')
        await textarea.press('End')
        await composer.getByRole('option').click()
        await expect(textarea).toHaveValue('Keep this @Alice ')
        await textarea.press('Home')
        const caret = await textarea.evaluate((el: HTMLTextAreaElement) => el.selectionStart)
        await composer.getByRole('button', { name: '이모티콘', exact: true }).click()
        const picker = page.getByRole('dialog')
        await picker.getByRole('button', { name: 'Test Pack 이미지 선택', exact: true }).click()
        await picker.locator('.image-btn').click()
        const parentId = reply ? 41 : null
        await expect.poll(() => writes).toEqual([{ content: '![emoticon](/uploads/test-sticker.png)', parentId }])
        await expect(textarea).toHaveValue('Keep this @Alice ')
        await expect(textarea).toBeFocused()
        expect(await textarea.evaluate((el: HTMLTextAreaElement) => el.selectionStart)).toBe(caret)
        await composer.locator('button[type="submit"]').click()
        await expect.poll(() => writes).toEqual([
          { content: '![emoticon](/uploads/test-sticker.png)', parentId },
          { content: 'Keep this @Alice', parentId, mentionedUserIds: [7] },
        ])
        if (reply) await expect(textarea).toHaveCount(0)
        else await expect(textarea).toHaveValue('')
      })
    }

    for (const replies of [false, true]) {
      test(`failed next ${replies ? 'reply' : 'comment'} page keeps an open draft through retry`, async ({ page }) => {
        await installMockApi(page, { authenticated: true })
        const items = Array.from({ length: 51 }, (_, index) => comment(index + 100))
        let failNextPage = true
        const reads: number[] = []
        const paginated = (route: Route) => {
          const number = Number(new URL(route.request().url()).searchParams.get('page') ?? 0)
          reads.push(number)
          if (number === 1 && failNextPage) return route.fulfill({ status: 500, json: { success: false, error: { message: 'Unavailable' } } })
          return json(route, pageData(items, number))
        }
        await page.route(/\/api\/v1\/posts\/1\/comments(?:\?.*)?$/, replies
          ? route => json(route, pageData([comment(41, true)])) : paginated)
        if (replies) await page.route('**/api/v1/comments/41/replies?*', paginated)
        await page.goto('/board/general/post/1')
        const container = replies ? page.locator('#comment-41') : page.locator('main')
        if (replies) await container.getByRole('button', { name: /답글 \d+개 보기/ }).click()
        const row = page.locator('#comment-100')
        await row.getByRole('button', { name: '답글', exact: true }).click()
        const textarea = row.getByRole('combobox')
        await textarea.fill('Keep my reply during loading failure')
        await container.getByRole('button', { name: replies ? '더 보기' : /댓글 더 보기/, exact: replies }).click()
        const error = container.getByRole('alert').filter({ hasText: replies ? '답글을 불러오지 못했습니다.' : '댓글을 불러오지 못했습니다.' })
        await expect(error).toBeVisible()
        await expect(textarea).toHaveValue('Keep my reply during loading failure')
        failNextPage = false
        await error.getByRole('button', { name: '다시 시도' }).click()
        await expect(page.locator('#comment-150')).toBeVisible()
        await expect(error).toHaveCount(0)
        await expect(textarea).toHaveValue('Keep my reply during loading failure')
        expect(reads.filter(number => number === 0)).toHaveLength(1)
        expect(reads.filter(number => number === 1).length).toBeGreaterThanOrEqual(2)
      })
    }
  })
}
