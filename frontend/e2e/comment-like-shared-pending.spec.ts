import { expect, test, type Route } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

function fulfillJson(route: Route, data: unknown) {
    return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data, error: null }),
    })
}

for (const width of [390, 1280]) {
    test(`best and regular comment instances share their like lock at ${width}px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 })
        await installMockApi(page, { authenticated: true })
        const liked = new Set<number>()
        const writes: Array<{ id: number, method: string }> = []
        let releaseFirstLike!: () => void
        const firstLikeGate = new Promise<void>(resolve => { releaseFirstLike = resolve })
        const comment = (commentId: number) => ({
            commentId,
            content: commentId === 41 ? 'A comment appearing in both lists' : 'An independent comment',
            author: { userId: 42, displayName: 'Another author', profileImageUrl: null },
            liked: liked.has(commentId),
            likeCount: 10 + Number(liked.has(commentId)),
            isDeleted: false, children: [], mentions: [], replyCount: 0, hasReplies: false,
            createdAt: '2026-01-01T00:00:00+09:00',
        })
        await page.route(/\/api\/v1\/posts\/1\/comments\/best(?:\?.*)?$/, route => fulfillJson(route, [comment(41)]))
        await page.route(/\/api\/v1\/posts\/1\/comments(?:\?.*)?$/, route => fulfillJson(route, {
            content: [comment(41), comment(42)], number: 0, size: 50, totalElements: 2,
            totalPages: 1, first: true, last: true,
        }))
        await page.route(/\/api\/v1\/comments\/(41|42)\/like$/, async route => {
            const id = Number(new URL(route.request().url()).pathname.split('/').at(-2))
            const method = route.request().method()
            writes.push({ id, method })
            if (id === 41 && method === 'POST') await firstLikeGate
            if (method === 'POST') liked.add(id)
            else {
                if (!liked.has(id)) {
                    await route.fulfill({
                        status: 400, contentType: 'application/json',
                        body: JSON.stringify({ success: false, error: { code: 'L002', message: 'NOT_LIKED' } }),
                    })
                    return
                }
                liked.delete(id)
            }
            await fulfillJson(route, null)
        })

        try {
            await page.goto('/board/general/post/1')
            const duplicated = page.locator('[id="comment-41"]')
            await expect(duplicated).toHaveCount(2)
            const bestLike = duplicated.nth(0).locator('button[aria-pressed]')
            const regularLike = duplicated.nth(1).locator('button[aria-pressed]')
            const otherLike = page.locator('#comment-42 button[aria-pressed]')
            await bestLike.click()
            await expect.poll(() => writes).toEqual([{ id: 41, method: 'POST' }])
            await expect(bestLike).toBeDisabled()
            await expect(regularLike).toBeDisabled()
            await expect(bestLike).toHaveAttribute('aria-pressed', 'true')
            await expect(regularLike).toHaveAttribute('aria-pressed', 'true')

            // A dispatched callback must also hit the feature guard, even if
            // it bypasses the native disabled-button behavior.
            await regularLike.dispatchEvent('click')
            await expect(otherLike).toBeEnabled()
            await otherLike.click()
            await expect.poll(() => liked.has(42)).toBe(true)
            await expect(otherLike).toBeEnabled()
            await expect(bestLike).toBeDisabled()
            await expect(regularLike).toBeDisabled()
            expect(writes.filter(write => write.id === 41)).toEqual([{ id: 41, method: 'POST' }])

            releaseFirstLike()
            await expect(bestLike).toBeEnabled()
            await expect(regularLike).toBeEnabled()
            await expect(bestLike).toHaveAttribute('aria-pressed', 'true')
            await expect(regularLike).toHaveAttribute('aria-pressed', 'true')
            await expect(bestLike).toHaveText('11')
            await expect(regularLike).toHaveText('11')

            await regularLike.click()
            await expect.poll(() => liked.has(41)).toBe(false)
            await expect(bestLike).toHaveAttribute('aria-pressed', 'false')
            await expect(regularLike).toHaveAttribute('aria-pressed', 'false')
            await expect(bestLike).toBeEnabled()
            await expect(regularLike).toBeEnabled()
            await expect(bestLike).toHaveText('10')
            await expect(regularLike).toHaveText('10')
            expect(writes.filter(write => write.id === 41)).toEqual([
                { id: 41, method: 'POST' }, { id: 41, method: 'DELETE' },
            ])
        } finally {
            releaseFirstLike()
        }
    })
}
