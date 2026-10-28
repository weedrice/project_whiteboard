import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`title validation explains the problem and focuses the field (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const state = await installMockApi(page, { authenticated: true })
    await page.goto('/board/general/write')

    const title = page.locator('#title')
    await title.fill('<Vue> title')
    await page.locator('.tiptap[contenteditable="true"]').fill('A valid body')
    await page.locator('.nv-compose-header button').last().click()

    const titleError = page.locator('#title-error')
    await expect(titleError).toContainText('제목에는 HTML 태그나 이벤트 속성을 사용할 수 없습니다.')
    await expect(title).toHaveAttribute('aria-invalid', 'true')
    await expect(title).toBeFocused()
    expect(state.writes.filter((write) => write.url === '/boards/general/posts')).toHaveLength(0)

    await expect(page.locator('.nv-toast-top-container [role="alert"]')).toHaveCount(0)
    await title.fill('Vue title')
    await expect(titleError).toBeHidden()
    await expect(title).not.toHaveAttribute('aria-invalid', 'true')
    await page.locator('.nv-compose-header button').last().click()
    await expect.poll(() => state.writes.filter((write) => write.url === '/boards/general/posts').length).toBe(1)
    expect(state.writes.find((write) => write.url === '/boards/general/posts')?.payload).toMatchObject({
      title: 'Vue title',
    })
  })
}
