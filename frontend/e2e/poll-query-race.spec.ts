import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const action of ['vote', 'cancel'] as const) {
  test(`preserves ${action} after an older like-triggered post response`, async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    const initialSelected = action === 'cancel'
    const makePoll = (selected: boolean) => ({
      pollId: 1, question: 'Which option?', multipleChoiceEnabled: false, anonymousEnabled: true,
      options: [{ optionId: 7, optionText: 'Option A', sortOrder: 0, voteCount: selected ? 1 : 0, selected }],
    })
    const original = {
      postId: 1, title: 'Poll race', contents: '<p>Post body</p>',
      viewCount: 1, likeCount: 0, commentCount: 0, isLiked: false, isScrapped: false,
      isNotice: false, isNsfw: false, isSpoiler: false, isSecret: false, isDeleted: false,
      createdAt: '2026-01-01T00:00:00', modifiedAt: '2026-01-01T00:00:00',
      board: { boardId: 1, boardName: 'General', boardUrl: 'general' },
      author: { userId: 7, displayName: 'Tester', profileImageUrl: null },
      tags: [], files: [], poll: makePoll(initialSelected),
    }
    let getCount = 0
    let releaseGet!: () => void
    let markGetStarted!: () => void
    let markGetFinished!: () => void
    const heldGet = new Promise<void>(resolve => { releaseGet = resolve })
    const getStarted = new Promise<void>(resolve => { markGetStarted = resolve })
    const getFinished = new Promise<void>(resolve => { markGetFinished = resolve })
    await page.route('**/api/v1/posts/1?*', async route => {
      getCount += 1
      if (getCount > 1) {
        markGetStarted()
        await heldGet
      }
      try {
        await route.fulfill({ json: { success: true, data: original } })
      } finally {
        if (getCount > 1) markGetFinished()
      }
    })
    await page.route('**/api/v1/posts/1/like', route => route.fulfill({ json: { success: true, data: 1 } }))
    await page.route('**/api/v1/posts/1/poll/vote', route => {
      expect(route.request().method()).toBe(action === 'vote' ? 'POST' : 'DELETE')
      return route.fulfill({ json: { success: true, data: makePoll(!initialSelected) } })
    })
    await page.goto('/board/general/post/1')
    const option = page.getByRole('radio', { name: /Option A/ })
    await expect(option).toBeVisible()
    await page.getByRole('button', { name: /^추천 \d+$/ }).click()
    await getStarted
    if (action === 'vote') {
      await option.check()
      await page.getByRole('button', { name: '투표', exact: true }).click()
      await expect(page.getByRole('button', { name: '내 투표 취소', exact: true })).toBeVisible()
    } else {
      await page.getByRole('button', { name: '내 투표 취소', exact: true }).click()
      await expect(option).not.toBeChecked()
    }
    releaseGet()
    await getFinished
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    if (action === 'vote') await expect(option).toBeChecked()
    else await expect(option).not.toBeChecked()
    await expect(page.getByText(action === 'vote' ? '1표' : '0표', { exact: true })).toBeVisible()
  })
}
