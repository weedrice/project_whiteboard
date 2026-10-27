import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [1280]) {
  test(`modal owns keyboard input at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const state = await installMockApi(page, {
      authenticated: true,
      postAuthor: { userId: 8, loginId: 'author', displayName: 'Author', profileImageUrl: null },
    })
    await page.goto('/board/general/post/1')
    await page.getByRole('button', { name: '신고', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '신고', exact: true })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '닫기', exact: true }).focus()
    const originalTheme = await page.locator('html').getAttribute('class')
    for (const key of ['q', 'h', 'd', '/', 'g', 'b', 'Control+k']) await page.keyboard.press(key)
    await expect(dialog).toBeVisible()
    await expect(page).toHaveURL(/\/board\/general\/post\/1$/)
    expect(state.authenticated).toBe(true)
    expect(await page.locator('html').getAttribute('class')).toBe(originalTheme)
    await dialog.locator('#reportReason').fill('Modal input remains available')
    await expect(dialog.locator('#reportReason')).toHaveValue('Modal input remains available')
    await page.keyboard.press('Escape')
    await expect(dialog).not.toBeVisible()
    await page.locator('h1').click()
    await page.keyboard.press('h')
    await expect(page).toHaveURL(/\/$/)
  })
}
