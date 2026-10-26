import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('submits a newly selected mention after removing earlier mention targets', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  await page.route('**/api/v1/users/mention-candidates?*', route => {
    const keyword = new URL(route.request().url()).searchParams.get('keyword')!
    const candidate = keyword === 'Bo'
      ? { userId: 99, displayName: 'Bob', profileImageUrl: null }
      : { userId: Number(keyword.replace('Old', '')) + 1, displayName: keyword, profileImageUrl: null }
    return route.fulfill({ json: { success: true, data: [candidate] } })
  })
  let submitted: { content: string; mentionedUserIds: number[] } | undefined
  await page.route('**/api/v1/posts/1/comments', async route => {
    submitted = route.request().postDataJSON()
    await route.fulfill({ json: { success: true, data: { commentId: 44 } } })
  })
  await page.goto('/board/general/post/1')
  const composer = page.locator('#comment-composer')
  const text = composer.getByRole('combobox')
  for (let index = 0; index < 10; index += 1) {
    const name = `Old${index}`
    await text.fill(`${await text.inputValue()}@${name}`)
    await text.press('End')
    await composer.getByRole('option').click()
    await expect(text).toHaveValue(new RegExp(`@${name} $`))
  }
  await text.fill('@Old0 @Bo')
  await text.press('End')
  await composer.getByRole('option').click()
  await expect(text).toHaveValue('@Old0 @Bob ')
  await composer.locator('button[type="submit"]').click()
  await expect.poll(() => submitted).toEqual({ content: '@Old0 @Bob', parentId: null, mentionedUserIds: [1, 99] })
  await expect(text).toHaveValue('')
})
