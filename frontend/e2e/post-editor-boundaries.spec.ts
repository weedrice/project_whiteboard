import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`Escape dismisses mention suggestions before exiting focus mode at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { postContents: '<p>Before</p>' })
    await page.route('**/api/v1/users/mention-candidates?*', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [{ userId: 2, displayName: 'Alice', profileImageUrl: null }] }),
    }))
    await login(page)
    await page.goto('/board/general/post/1/edit')
    await page.getByRole('button', { name: '집중 모드', exact: true }).click()
    const editor = page.locator('.ProseMirror')
    await editor.getByText('Before', { exact: true }).click()
    await expect(editor).toBeFocused()
    await page.keyboard.press('End')
    await page.keyboard.type(' @Al')
    await expect(page.getByRole('option', { name: 'Alice' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('.post-editor-focus')).toHaveAttribute('aria-modal', 'true')
    await expect(page.locator('.mention-suggestion-menu')).toHaveCount(0)
    await expect(editor).toBeFocused()
    await expect(editor).toHaveText('Before @Al')
    await page.keyboard.press('Escape')
    await expect(page.locator('.post-editor-focus')).toHaveCount(0)
    expect(page.url()).toMatch(/\/edit\/?$/)
  })

  test(`focus mode contains keyboard focus and supports nested link dialogs at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { postContents: '<p>Alpha Alpha</p>' })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    await page.getByRole('button', { name: '집중 모드', exact: true }).click()
    const focusDialog = page.getByRole('dialog', { name: '집중 모드', exact: true })
    await expect(focusDialog).toHaveAttribute('aria-modal', 'true')
    const editor = page.locator('.ProseMirror')
    await editor.focus()
    await page.keyboard.press('Tab')
    await expect.poll(() => page.evaluate(() => !!document.activeElement?.closest('.post-editor-focus'))).toBe(true)
    await page.keyboard.press('Shift+Tab')
    await expect.poll(() => page.evaluate(() => !!document.activeElement?.closest('.post-editor-focus'))).toBe(true)
    expect(await page.locator('#category').evaluate(element => !!element.closest('[inert]'))).toBe(true)

    await editor.locator('p').first().click()
    await page.keyboard.press('Home')
    await page.keyboard.press('Shift+End')
    await page.getByRole('group', { name: '선택 영역 도구' }).getByRole('button', { name: '링크', exact: true }).click()
    const links = page.getByRole('dialog', { name: '링크 설정', exact: true })
    await links.locator('#editor-link-url').fill('https://example.com/focus')
    await links.locator('#editor-link-url').press('Enter')
    await expect(links).toHaveCount(0)
    await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.com/focus')
    await expect(focusDialog).toHaveAttribute('aria-modal', 'true')

    await editor.focus()
    await page.keyboard.press('Tab')
    await expect.poll(() => page.evaluate(() => !!document.activeElement?.closest('.post-editor-focus'))).toBe(true)
    await page.keyboard.press('Escape')
    await expect(page.locator('.post-editor-focus')).toHaveCount(0)
    expect(page.url()).toMatch(/\/edit\/?$/)
    expect(await page.locator('#category').evaluate(element => !!element.closest('[inert]'))).toBe(false)
  })
}

test('mobile selection tools hide when the selected text scrolls outside the editing surface', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 1000 })
  await installMockApi(page, { postContents: '<p>Alpha Alpha</p>' + '<p>Other text</p>'.repeat(35) })
  await login(page)
  await page.goto('/board/general/post/1/edit')
  await page.locator('.ProseMirror p').first().click()
  await page.keyboard.press('Home')
  await page.keyboard.press('Shift+End')
  const tools = page.getByRole('group', { name: '선택 영역 도구' })
  await expect(tools).toBeVisible()
  await page.locator('.tiptap-content').evaluate(element => { element.scrollTop += 90 })
  await expect(tools).not.toBeVisible()
  await page.locator('.tiptap-content').evaluate(element => { element.scrollTop = 0 })
  await expect(tools).toBeVisible()
})

for (const [name, html] of [
  ['checklist', '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><h2>Task heading</h2><p>Task body</p></li></ul>'],
  ['details summary', '<details><summary><h2>Heading</h2></summary><div data-details-content=""><p>Body</p></div></details>'],
]) {
  test(`original paste preserves ${name} structures unsupported by the editor schema`, async ({ page }) => {
    await installMockApi(page, { postContents: '<p>Before</p>' })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    await editor.locator('p').click()
    await page.keyboard.press('End')
    await editor.evaluate((element, content) => {
      const clipboardData = new DataTransfer()
      clipboardData.setData('text/html', content)
      clipboardData.setData('text/plain', 'Heading\nBody')
      element.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }))
    }, html)
    await expect(editor.locator('.raw-html-block')).toHaveCount(1)
    await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
    await page.waitForURL(/\/board\/general\/post\/1\/?$/)
    await page.goto('/board/general/post/1/edit')
    await expect(editor.locator('.raw-html-block')).toHaveCount(1)
    await expect(editor.locator('h2')).toHaveCount(0)
  })
}
