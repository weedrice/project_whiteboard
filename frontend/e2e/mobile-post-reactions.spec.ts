import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [320, 390]) {
  test(`post reactions and report remain reachable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, {
      authenticated: true,
      postAuthor: { userId: 8, loginId: 'author', displayName: 'Author', profileImageUrl: null },
    })
    let likes = 0
    let scraps = 0
    await page.route('**/api/v1/posts/1/like', async route => {
      likes += 1
      await route.fulfill({ json: { success: true, data: 1 } })
    })
    await page.route('**/api/v1/posts/1/scrap', async route => {
      scraps += 1
      await route.fulfill({ json: { success: true, data: null } })
    })
    await page.goto('/board/general/post/1')
    const actions = page.locator('.nv-post-reaction-row')
    await expect(actions.getByRole('button')).toHaveCount(4)
    await actions.scrollIntoViewIfNeeded()
    for (const button of await actions.getByRole('button').all()) await expect(button).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await actions.getByRole('button', { name: /^추천 \d+$/ }).click()
    await expect.poll(() => likes).toBe(1)
    await actions.getByRole('button', { name: '북마크', exact: true }).click()
    await expect.poll(() => scraps).toBe(1)
    await actions.getByRole('button', { name: '신고', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '신고', exact: true })).toBeVisible()
  })
}
