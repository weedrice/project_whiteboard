import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`post editor exposes a named multiline textbox at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await installMockApi(page, { authenticated: true })
    await page.goto('/board/general/write')
    const editor = page.getByRole('textbox', { name: '내용', exact: true })
    await expect(editor).toBeVisible()
    await expect(editor).toHaveAttribute('aria-multiline', 'true')
    await editor.fill('Accessible draft body')
    await expect(editor).toHaveText('Accessible draft body')
    const result = await new AxeBuilder({ page }).include('.tiptap-editor-wrap').analyze()
    expect(result.violations.filter(({ impact }) => impact === 'serious' || impact === 'critical')).toEqual([])
  })

  test(`post back-to-list action has a name and supports the keyboard at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await installMockApi(page)
    await page.goto('/board/general/post/1')
    const back = page.locator('.nv-post-back-btn')
    await expect(back).toHaveAccessibleName('목록으로')
    await back.focus()
    await expect(back).toBeFocused()
    const result = await new AxeBuilder({ page }).include('.nv-post-header').analyze()
    expect(result.violations.filter(({ impact }) => impact === 'serious' || impact === 'critical')).toEqual([])
    await back.press('Enter')
    await expect(page).toHaveURL(/\/board\/general\/?(?:\?.*)?$/)
  })
}
