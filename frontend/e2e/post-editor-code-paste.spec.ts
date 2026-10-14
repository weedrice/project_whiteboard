import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

for (const withHtml of [false, true]) {
  test(`VS Code paste stays editable after saving with HTML=${withHtml}`, async ({ page }) => {
    await installMockApi(page, { postContents: '<p>Before</p><p></p>' })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    await editor.locator('p').last().click()
    await editor.evaluate((element, html) => {
      const clipboardData = new DataTransfer()
      clipboardData.setData('text/plain', 'const answer = 42;\r\n  // Code')
      clipboardData.setData('vscode-editor-data', JSON.stringify({ mode: 'typescript' }))
      if (html) clipboardData.setData('text/html', '<div style="font-family:Consolas"><span style="color:blue">const</span> answer = 42;</div>')
      element.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }))
    }, withHtml)
    await expect(editor.locator('pre code')).toHaveText('const answer = 42;\n  // Code', { useInnerText: false })
    await expect(editor.locator('pre code')).toHaveAttribute('class', 'language-typescript')
    await expect(page.locator('.raw-html-block')).toHaveCount(0)
    await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
    await page.waitForURL(/\/board\/general\/post\/1\/?$/)
    await page.goto('/board/general/post/1/edit')
    await expect(editor.locator('pre code')).toHaveAttribute('class', 'language-typescript')
    await expect(editor.locator('pre code')).toHaveText('const answer = 42;\n  // Code', { useInnerText: false })
    await expect(page.locator('.raw-html-block')).toHaveCount(0)
  })
}

test('formatted paste inside code preserves its language and supports undo', async ({ page }) => {
  await installMockApi(page, { postContents: '<pre><code class="language-typescript">// Before</code></pre>' })
  await login(page)
  await page.goto('/board/general/post/1/edit')
  const editor = page.locator('.ProseMirror')
  await editor.locator('pre').click()
  await page.keyboard.press('End')
  await editor.evaluate(element => {
    const clipboardData = new DataTransfer()
    clipboardData.setData('text/plain', '\r\n  const answer = "<tag>";')
    clipboardData.setData('text/html', '<div style="font-family:Consolas"><span style="color:blue">const</span> answer = "&lt;tag&gt;";</div>')
    clipboardData.setData('vscode-editor-data', JSON.stringify({ mode: 'javascript' }))
    element.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }))
  })
  const expected = '// Before\n  const answer = "<tag>";'
  await expect(editor.locator('pre code')).toHaveText(expected, { useInnerText: false })
  await expect(editor.locator('pre code')).toHaveAttribute('class', 'language-typescript')
  await expect(page.locator('.raw-html-block')).toHaveCount(0)
  await page.keyboard.press('Control+z')
  await expect(editor.locator('pre code')).toHaveText('// Before')
  await page.keyboard.press('Control+Shift+z')
  await expect(editor.locator('pre code')).toHaveText(expected, { useInnerText: false })
})

test('post display keeps the complete C++ language identifier', async ({ page }) => {
  await installMockApi(page, { postContents: '<pre><code class="language-c++">std::vector&lt;int&gt; values;</code></pre>' })
  await login(page)
  await page.goto('/board/general/post/1')
  await expect(page.locator('.nv-code-block-language')).toHaveText('c++')
  await expect(page.locator('.nv-code-block pre code')).toHaveText('std::vector<int> values;')
  await expect(page.locator('.nv-code-block pre code')).not.toHaveClass(/(?:^|\s)language-c(?:\s|$)/)
})
