import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'
import { encodeSandboxedPostHtml } from '../src/utils/postHtmlSandbox'

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze()
  const violations = results.violations.filter((violation) =>
    violation.impact === 'serious' || violation.impact === 'critical')
  expect(violations, JSON.stringify(violations, null, 2)).toEqual([])
}

const dialogViewports = [
  { name: 'mobile', size: { width: 390, height: 844 } },
  { name: 'desktop', size: { width: 1280, height: 800 } },
] as const

test('login and home have no serious or critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/login')
  await expectNoSeriousAccessibilityViolations(page)
  await page.goto('/')
  await expectNoSeriousAccessibilityViolations(page)
})

test('post detail has no serious or critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/board/general/post/1')
  await expect(page.getByRole('heading', { name: 'Accessible post' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('settings and post editor have no serious or critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await login(page)
  await page.goto('/mypage/settings')
  await expectNoSeriousAccessibilityViolations(page)
  await page.goto('/board/general/write')
  await expect(page.locator('#title')).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('post editor with a selected preserved HTML block is axe-clean', async ({ page }) => {
  const preserved = encodeSandboxedPostHtml(
    '<style>.card{display:grid}</style><section class="card">접근성 HTML 블록</section>',
  )
  await installMockApi(page, { postContents: preserved })
  await login(page)
  await page.goto('/board/general/post/1/edit')

  const htmlBlock = page.locator('.raw-html-block')
  await expect(htmlBlock).toBeVisible()
  await htmlBlock.locator('.raw-html-block__header').click()
  await expect(page.locator('.tiptap-toolbar-context')).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('opened notification dialog is axe-clean and restores its trigger on Escape', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await installMockApi(page)
  await login(page)
  const trigger = page.locator('button[aria-controls="notification-dropdown-panel"]')
  await trigger.click()

  await expect(page.locator('#notification-dropdown-panel [role="dialog"]')).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
  await page.keyboard.press('Escape')

  await expect(page.locator('#notification-dropdown-panel')).toBeHidden()
  await expect(trigger).toBeFocused()
})

for (const viewport of dialogViewports) {
  test(`opened editor popover uses the shared dialog surface and is axe-clean (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page)
    await login(page)
    await page.goto('/board/general/write')

    const trigger = page.locator('button[aria-controls="editor-table-dialog"]')
    await trigger.click()

    const dialog = page.locator('#editor-table-dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toHaveClass(/nv-dialog-surface--popover/)
    await expectNoSeriousAccessibilityViolations(page)
    await page.keyboard.press('Escape')

    await expect(dialog).toBeHidden()
    await expect(trigger).toBeFocused()
  })

  test(`image lightbox uses the media dialog variant and is axe-clean (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page, {
      postContents: '<p><img src="/images/default-emoticon.png" alt="Preview image"></p>',
    })
    await page.goto('/board/general/post/1')

    const trigger = page.locator('img[alt="Preview image"]')
    await trigger.click()

    const dialog = page.getByRole('dialog', { name: 'Accessible post' })
    await expect(dialog).toBeVisible()
    await expect(dialog).toHaveClass(/nv-dialog-overlay--media/)
    await expectNoSeriousAccessibilityViolations(page)
    await page.keyboard.press('Escape')

    await expect(dialog).toBeHidden()
    await expect(trigger).toBeFocused()
  })
}

test('opened mobile write sheet is axe-clean, traps focus, and restores its trigger', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installMockApi(page)
  await login(page)
  const trigger = page.locator('button[aria-controls="mobile-write-sheet"]')
  await trigger.click()

  const dialog = page.locator('#mobile-write-sheet')
  await expect(dialog).toBeVisible()
  await expect(dialog).toBeFocused()
  await expectNoSeriousAccessibilityViolations(page)

  await page.keyboard.press('Shift+Tab')
  await expect(dialog.locator('button').last()).toBeFocused()
  await page.keyboard.press('Escape')

  await expect(dialog).toBeHidden()
  await expect(trigger).toBeFocused()
})

for (const viewport of dialogViewports) {
  test(`message and report dialogs use the standard modal contract and are axe-clean (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page, {
      postAuthor: { userId: 8, loginId: 'writer', displayName: '작성자', profileImageUrl: null },
    })
    await login(page)
    await page.goto('/board/general/post/1')

    const authorMenu = page.getByRole('button', { name: '작성자' })
    await authorMenu.click()
    await page.getByRole('menuitem', { name: '쪽지 보내기' }).click()

    const messageDialog = page.getByRole('dialog', { name: '쪽지 보내기' })
    await expect(messageDialog).toBeVisible()
    await expect(messageDialog.locator('.modal-body > .nv-dialog-stack')).toBeVisible()
    await expectNoSeriousAccessibilityViolations(page)
    await page.keyboard.press('Escape')
    await expect(messageDialog).toBeHidden()

    await authorMenu.click()
    await page.getByRole('menuitem', { name: '신고하기' }).click()

    const reportDialog = page.getByRole('dialog', { name: '사용자 신고' })
    await expect(reportDialog).toBeVisible()
    await expect(reportDialog.locator('.modal-footer > button')).toHaveCount(2)
    await expectNoSeriousAccessibilityViolations(page)
    await page.keyboard.press('Escape')
    await expect(reportDialog).toBeHidden()
  })

  test(`video and emoticon popovers share the topmost dialog contract (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page)
    await login(page)
    await page.goto('/board/general/write')

    const videoTrigger = page.getByRole('button', { name: '동영상' })
    await videoTrigger.click()
    const videoDialog = page.getByRole('dialog', { name: '동영상 URL' })
    await expect(videoDialog).toHaveAttribute('aria-modal', 'true')
    await expect(videoDialog).toHaveClass(/nv-dialog-surface--popover/)
    await expectNoSeriousAccessibilityViolations(page)
    await page.keyboard.press('Escape')
    await expect(videoDialog).toBeHidden()

    const emoticonTrigger = page.getByRole('button', { name: '이모티콘' })
    await emoticonTrigger.click()
    const emoticonDialog = page.getByRole('dialog', { name: '노비콘' })
    await expect(emoticonDialog).toHaveAttribute('aria-modal', 'true')
    await expect(emoticonDialog).toHaveClass(/nv-dialog-surface--popover/)
    await expectNoSeriousAccessibilityViolations(page)
    await page.keyboard.press('Escape')
    await expect(emoticonDialog).toBeHidden()
  })
}
