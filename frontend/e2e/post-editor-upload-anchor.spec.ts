import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7XkAAAAASUVORK5CYII=', 'base64')

for (const width of [390, 1280]) {
  test(`delayed image uploads preserve selected text and batch order at ${width}px`, async ({ page }) => {
    const releases: Array<() => void> = []
    const gates = Array.from({ length: 3 }, () => new Promise<void>(resolve => releases.push(resolve)))
    let requests = 0
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { postContents: '<p>start KEEP end</p>' })
    await page.route('**/api/v1/files/upload', async route => {
      const index = requests++
      await gates[index]
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({
        success: true, data: { fileId: 91 + index, fileUrl: `/api/v1/files/${91 + index}` },
      }) })
    })
    try {
      await login(page)
      await page.goto('/board/general/post/1/edit')
      const editor = page.locator('.ProseMirror')
      const images = editor.locator('.post-editor-image-node > img')
      await editor.getByText('start KEEP end', { exact: true }).click()
      await page.keyboard.press('Home')
      await page.locator('.tiptap-editor-wrap input[type="file"][multiple]').setInputFiles(
        [1, 2, 3].map(index => ({ name: `image-${index}.png`, mimeType: 'image/png', buffer: png })),
      )
      await expect.poll(() => requests).toBe(1)
      // File selection can move focus. Re-enter the editor without changing the
      // already-captured upload anchor before selecting unrelated text.
      await editor.getByText('start KEEP end', { exact: true }).click()
      await page.keyboard.press('Home')
      for (let index = 0; index < 6; index++) await page.keyboard.press('ArrowRight')
      for (let index = 0; index < 4; index++) await page.keyboard.press('Shift+ArrowRight')
      await expect.poll(() => page.evaluate(() => window.getSelection()?.toString())).toBe('KEEP')
      await expect(page.getByRole('button', { name: '수정 완료', exact: true }).first()).toBeDisabled()

      for (let index = 0; index < 3; index++) {
        releases[index]!()
        await expect(images).toHaveCount(index + 1)
        await expect(editor).toContainText('start KEEP end')
        await expect.poll(() => page.evaluate(() => window.getSelection()?.toString())).toBe('KEEP')
      }
      const save = page.waitForRequest(request => request.method() === 'PUT' && request.url().endsWith('/posts/1'))
      await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
      const html: string = (await save).postDataJSON().contents
      expect(html).toContain('start KEEP end')
      // Submission strips editor-only file metadata and restores canonical src URLs.
      const savedImageSources = await page.evaluate(content => (
        Array.from(new DOMParser().parseFromString(content, 'text/html').querySelectorAll('img'))
          .map(image => image.getAttribute('src'))
      ), html)
      expect(savedImageSources).toEqual(['/api/v1/files/91', '/api/v1/files/92', '/api/v1/files/93'])
      expect(html.indexOf('<img')).toBeLessThan(html.indexOf('start KEEP end'))
    } finally {
      releases.forEach(release => release())
    }
  })
}
