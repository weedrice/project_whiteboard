import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const source of ['purchased', 'my']) {
  test(`searches and inserts a pack from the second ${source} page`, async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    const packs = Array.from({ length: 101 }, (_, index) => ({
      emoticonId: index + 1, name: `Pack${index + 1}`, tags: [`tag${index + 1}`],
      isActive: true, createdAt: '2026-01-01', modifiedAt: '2026-01-01', images: [],
    }))
    const requests: number[] = []
    for (const endpoint of ['purchased', 'my']) {
      await page.route(`**/api/v1/emoticons/${endpoint}?*`, route => {
        const params = new URL(route.request().url()).searchParams
        const pageNumber = Number(params.get('page') ?? 0)
        const items = endpoint === source ? packs : []
        if (endpoint === source) requests.push(pageNumber)
        return route.fulfill({ json: { success: true, data: {
          content: items.slice(pageNumber * 100, (pageNumber + 1) * 100), page: pageNumber, size: 100,
          totalElements: items.length, totalPages: Math.ceil(items.length / 100), hasNext: pageNumber === 0 && items.length > 100,
        } } })
      })
    }
    await page.route('**/api/v1/emoticons/101', route => route.fulfill({ json: { success: true, data: {
      ...packs[100], images: [{ imageId: 1010, emoticonId: 101, imageUrl: '/uploads/pack101.png', sortOrder: 0 }],
    } } }))
    let submitted: { content: string } | undefined
    await page.route('**/api/v1/posts/1/comments', async route => {
      submitted = route.request().postDataJSON()
      await route.fulfill({ json: { success: true, data: { commentId: 44 } } })
    })
    await page.goto('/board/general/post/1')
    const composer = page.locator('#comment-composer')
    await composer.getByRole('button', { name: '이모티콘', exact: true }).click()
    const picker = page.getByRole('dialog')
    await expect(picker).toHaveAccessibleName('노비콘')
    await picker.getByRole('textbox', { name: '노비콘 검색' }).fill('tag101')
    await picker.getByRole('button', { name: 'Pack101 이미지 선택', exact: true }).click()
    await picker.locator('.image-btn').click()
    await expect.poll(() => submitted?.content).toBe('![emoticon](/uploads/pack101.png)')
    expect(requests).toEqual([0, 1])
  })
}
