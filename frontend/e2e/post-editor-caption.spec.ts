import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

const captions = [
  { name: 'event-like text', text: 'example onload=demo' },
  { name: 'HTML example', text: '<script>window.captionExecuted = true</script>' },
]

for (const width of [390, 1280]) {
  for (const caption of captions) {
    test(`${caption.name} remains text through caption editing, save and reload at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 1000 })
      await installMockApi(page, { postContents: '<p>Before</p><p><img src="https://cdn.noviis.kr/test.png" width="240" alt="Diagram"></p><p>After</p>' })
      await page.route('https://cdn.noviis.kr/test.png', route => route.fulfill({
        contentType: 'image/png',
        body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7XkAAAAASUVORK5CYII=', 'base64'),
      }))
      await login(page)
      await page.goto('/board/general/post/1/edit')
      const editor = page.locator('.ProseMirror')
      await editor.locator('.post-editor-image-node > img').click()
      await page.getByRole('button', { name: '이미지 설명', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '이미지 설명', exact: true })
      await dialog.getByLabel('캡션', { exact: true }).fill(caption.text)
      await dialog.getByRole('button', { name: '저장', exact: true }).click()
      await expect(editor.locator('.post-editor-image-caption')).toHaveText(caption.text)
      await expect(page.locator('.raw-html-block')).toHaveCount(0)
      await expect(editor).toContainText('Before')
      await expect(editor).toContainText('After')

      await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
      await page.waitForURL(/\/board\/general\/post\/1\/?$/)
      await expect(page.locator('.nv-image-caption')).toHaveText(caption.text)
      await expect(page.locator('.nv-rich-content script')).toHaveCount(0)
      expect(await page.evaluate(() => Reflect.get(window, 'captionExecuted'))).toBeUndefined()

      await page.goto('/board/general/post/1/edit')
      await expect(editor.locator('.post-editor-image-caption')).toHaveText(caption.text)
      await expect(page.locator('.raw-html-block')).toHaveCount(0)
      await expect(editor).toContainText('Before')
      await expect(editor).toContainText('After')
    })
  }
}
