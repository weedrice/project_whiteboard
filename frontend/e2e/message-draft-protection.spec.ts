import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

const viewports = [
  { name: 'desktop', size: { width: 1280, height: 800 } },
  { name: 'mobile', size: { width: 390, height: 844 } },
] as const

for (const viewport of viewports) {
  test(`message drafts survive cancelled nested confirmation and discard only on approval (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page, {
      authenticated: true,
      postAuthor: { userId: 8, loginId: 'other', displayName: 'Other Author', profileImageUrl: null },
    })
    await page.goto('/board/general/post/1')
    const author = page.locator('.nv-user-menu-button[title="Other Author"]').first()
    const openMessage = async () => {
      await author.click()
      await page.getByRole('menuitem', { name: '쪽지 보내기', exact: true }).click()
    }
    const message = page.getByRole('dialog', { name: '쪽지 보내기', exact: true, includeHidden: true })
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    const content = message.getByRole('textbox', { name: '내용', exact: true, includeHidden: true })

    await openMessage()
    await content.fill('Keep this private draft')
    await content.press('Escape')
    await expect(confirmation).toBeVisible()
    await expect(confirmation.getByText('작성 중인 쪽지를 삭제하고 닫으시겠습니까?', { exact: true })).toBeVisible()
    await expect(message).toHaveAttribute('aria-hidden', 'true')
    await expect(message).toHaveAttribute('inert', '')
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(confirmation).toHaveCount(0)
    await expect(content).toHaveValue('Keep this private draft')
    await expect(content).toBeFocused()

    // Escape dismisses only the top confirmation, preserving the message below it.
    await content.press('Escape')
    await expect(confirmation).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(confirmation).toHaveCount(0)
    await expect(content).toHaveValue('Keep this private draft')

    await message.getByRole('button', { name: '취소', exact: true }).click()
    await expect(confirmation).toBeVisible()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(message).toHaveCount(0)
    await expect(confirmation).toHaveCount(0)

    await openMessage()
    await expect(content).toHaveValue('')
    await message.getByRole('button', { name: '취소', exact: true }).click()
    await expect(message).toHaveCount(0)
    await expect(confirmation).toHaveCount(0)
  })
}

for (const viewport of viewports) {
  test(`leaving the message owner dismisses its pending discard dialog (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page, {
      authenticated: true,
      postAuthor: { userId: 8, loginId: 'other', displayName: 'Other Author', profileImageUrl: null },
    })
    await page.route('**/api/v1/boards/general/posts?**', (route) => route.fulfill({ json: {
      success: true,
      data: {
        content: [{
          postId: 1, title: 'Accessible post', boardUrl: 'general', boardName: 'General',
          author: { userId: 8, displayName: 'Other Author' }, createdAt: '2026-10-01T09:00:00',
          likeCount: 0, viewCount: 0, commentCount: 0,
        }],
        number: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
      },
    } }))
    await page.goto('/board/general/')
    await page.getByRole('link', { name: 'Accessible post', exact: true }).click()
    await expect(page).toHaveURL(/\/board\/general\/post\/1\/?$/)
    await page.locator('.nv-user-menu-button[title="Other Author"]').first().click()
    await page.getByRole('menuitem', { name: '쪽지 보내기', exact: true }).click()
    const message = page.getByRole('dialog', { name: '쪽지 보내기', exact: true })
    await message.getByRole('textbox', { name: '내용', exact: true }).fill('Draft on the previous page')
    await message.getByRole('button', { name: '취소', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '확인', exact: true })).toBeVisible()
    await page.goBack()
    await expect(page).toHaveURL(/\/board\/general\/?$/)
    await expect(page.getByRole('dialog', { includeHidden: true })).toHaveCount(0)
    await expect(page.locator('main')).not.toHaveAttribute('inert', '')
    await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden')
  })
}
