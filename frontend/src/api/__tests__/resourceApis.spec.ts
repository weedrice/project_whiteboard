import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

import { boardApi } from '../board'
import { postApi } from '../post'
import { searchApi } from '../search'
import { fileApi } from '../file'
import { emoticonApi } from '../emoticon'
import { adApi } from '../ad'
import { notificationApi } from '../notification'
import { codeApi } from '../code'
import { reportApi } from '../report'

describe('boardApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls board endpoints with correct path and payload', () => {
        const boardData = { name: 'General', description: 'desc' }
        const updateData = { displayName: 'General Board' }
        const categoryData = { name: 'Notice', sortOrder: 1 }
        const categoryUpdateData = { name: 'Updated', isActive: true }
        const params = { page: 0, size: 20, categoryId: 7, sort: 'latest' }

        boardApi.getBoards()
        boardApi.getBoard('general', { skipAuthRefresh: true })
        boardApi.createBoard(boardData as never)
        boardApi.getPosts('general', params)
        boardApi.getCategories('general')
        boardApi.updateBoard('general', updateData as never)
        boardApi.updateBoardManager('general', { loginId: 'manager' })
        boardApi.getBoardManagerCandidates('general', { q: 'manager', page: 0, size: 10 })
        boardApi.deleteBoard('general')
        boardApi.createCategory('general', categoryData)
        boardApi.updateCategory('general', 12, categoryUpdateData)
        boardApi.deleteCategory('general', 12)
        boardApi.getNotices('general')
        boardApi.subscribeBoard('general')
        boardApi.unsubscribeBoard('general')
        boardApi.updateSubscriptionOrder(['general', 'tech'])

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/boards')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/boards/general', { skipAuthRefresh: true })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/boards', boardData)
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/boards/general/posts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/boards/general/categories')
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/boards/general', updateData)
        expect(apiMock.put).toHaveBeenNthCalledWith(2, '/boards/general/manager', { loginId: 'manager' })
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/boards/general/manager-candidates', { params: { q: 'manager', page: 0, size: 10 } })
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/boards/general')
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/boards/general/categories', categoryData)
        expect(apiMock.put).toHaveBeenNthCalledWith(3, '/boards/categories/12', categoryUpdateData)
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/boards/categories/12')
        expect(apiMock.get).toHaveBeenNthCalledWith(6, '/boards/general/notices')
        expect(apiMock.post).toHaveBeenNthCalledWith(3, '/boards/general/subscribe')
        expect(apiMock.delete).toHaveBeenNthCalledWith(3, '/boards/general/subscribe')
        expect(apiMock.put).toHaveBeenNthCalledWith(4, '/boards/subscriptions/order', { boardUrls: ['general', 'tech'] })
    })
})

describe('postApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls post endpoints with correct path and payload', () => {
        const postData = { title: 'title', contents: 'body', isNotice: false }
        const updateData = { title: 'updated title', tags: ['a'] }
        const reportData = { targetPostId: 3, reason: 'spam' }
        const requestConfig = { skipAuthRefresh: true }

        postApi.createPost('general', postData)
        postApi.getPost(3, requestConfig)
        postApi.updatePost(3, updateData)
        postApi.deletePost(3)
        postApi.incrementView(3)
        postApi.likePost(3)
        postApi.unlikePost(3)
        postApi.scrapPost(3)
        postApi.unscrapPost(3)
        postApi.getTrendingPosts()
        postApi.getTrendingPosts(2, 5)
        postApi.getHomeLanding('7d')
        postApi.reportPost(reportData)

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/boards/general/posts', postData)
        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/posts/3', requestConfig)
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/posts/3', updateData)
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/posts/3')
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/posts/3/view')
        expect(apiMock.post).toHaveBeenNthCalledWith(3, '/posts/3/like')
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/posts/3/like')
        expect(apiMock.post).toHaveBeenNthCalledWith(4, '/posts/3/scrap')
        expect(apiMock.delete).toHaveBeenNthCalledWith(3, '/posts/3/scrap')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/posts/trending', { params: { page: 0, size: 10, period: '24h' } })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/posts/trending', { params: { page: 2, size: 5, period: '24h' } })
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/home/landing', { params: { period: '7d' } })
        expect(apiMock.post).toHaveBeenNthCalledWith(5, '/reports/posts', reportData)
    })
})

describe('reportApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls report endpoints with correct path and payload', () => {
        reportApi.reportUser(7, 'abuse', '/users/7')
        reportApi.reportPost(11, 'spam')
        reportApi.reportComment(13, 'off-topic')
        reportApi.getMyReports({ page: 1, size: 10 })

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/reports/users', {
            targetUserId: 7,
            reason: 'abuse',
            link: '/users/7',
        })
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/reports/posts', {
            targetPostId: 11,
            reason: 'spam',
        })
        expect(apiMock.post).toHaveBeenNthCalledWith(3, '/reports/comments', {
            targetCommentId: 13,
            reason: 'off-topic',
        })
        expect(apiMock.get).toHaveBeenCalledWith('/reports/me', { params: { page: 1, size: 10 } })
    })
})

describe('searchApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls search endpoints with params', async () => {
        const params = { keyword: 'vue', page: 1, size: 20, type: 'post' }
        apiMock.get.mockResolvedValue({
            data: {
                success: true,
                data: {
                    keywords: [
                        { rank: 1, keyword: 'vue', count: 10 },
                    ],
                },
            },
        })

        searchApi.search(params as never)
        searchApi.searchPosts(params as never)
        const response = await searchApi.getPopularKeywords()

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/search', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/search/posts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/search/popular')
        expect(response.data).toEqual({
            success: true,
            data: [{ keyword: 'vue', count: 10 }],
        })
    })
})

describe('codeApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls common code detail endpoint and maps backend fields', async () => {
        apiMock.get.mockResolvedValueOnce({
            data: {
                success: true,
                data: [
                    {
                        codeValue: 'NOTICE',
                        codeName: 'Notice',
                        sortOrder: 1,
                    },
                ],
            },
        })

        const response = await codeApi.getCodes('BOARD_TYPE')

        expect(apiMock.get).toHaveBeenCalledWith('/common-codes/BOARD_TYPE/details')
        expect(response.data).toEqual({
            success: true,
            data: [
                {
                    code: 'NOTICE',
                    value: 'NOTICE',
                    name: 'Notice',
                    sortOrder: 1,
                },
            ],
        })
    })
})

describe('fileApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('builds formData and applies multipart header', () => {
        const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })

        fileApi.uploadFile(file)

        expect(apiMock.post).toHaveBeenCalledWith(
            '/files/upload',
            expect.any(FormData),
            expect.objectContaining({
                headers: expect.objectContaining({
                    'Content-Type': 'multipart/form-data',
                }),
            }),
        )
    })

    it('merges custom headers from config', () => {
        const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })
        const config = {
            timeout: 5000,
            headers: {
                Authorization: 'Bearer token',
            },
        }

        fileApi.uploadFile(file, config)

        expect(apiMock.post).toHaveBeenCalledWith(
            '/files/upload',
            expect.any(FormData),
            expect.objectContaining({
                timeout: 5000,
                headers: expect.objectContaining({
                    'Content-Type': 'multipart/form-data',
                    Authorization: 'Bearer token',
                }),
            }),
        )
    })
})

describe('adApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls ad endpoints and unwraps response envelopes', async () => {
        const ad = { adId: 1, title: 'Ad', imageUrl: null, targetUrl: 'https://example.com' }
        apiMock.get.mockResolvedValueOnce({ data: { success: true, data: ad } })
        apiMock.post
            .mockResolvedValueOnce({ data: { success: true } })
            .mockResolvedValueOnce({ data: { success: true, data: 'https://example.com' } })

        await expect(adApi.getAd('SIDEBAR')).resolves.toEqual(ad)
        await adApi.recordImpression(1)
        await expect(adApi.recordClick(1)).resolves.toBe('https://example.com')

        expect(apiMock.get).toHaveBeenCalledWith('/ads', { params: { placement: 'SIDEBAR' } })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/ads/1/impression', undefined, undefined)
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/ads/1/click')
    })
})

describe('notificationApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('opens notification stream through fetch using API base URL', async () => {
        const response = new Response(null, { status: 200 })
        const fetchMock = vi.fn().mockResolvedValue(response)
        vi.stubGlobal('fetch', fetchMock)
        const controller = new AbortController()

        await expect(notificationApi.openStream('token', controller.signal)).resolves.toBe(response)

        expect(fetchMock).toHaveBeenCalledWith('/api/v1/notifications/stream', {
            method: 'GET',
            headers: {
                Accept: 'text/event-stream',
                Authorization: 'Bearer token',
            },
            cache: 'no-store',
            credentials: 'same-origin',
            signal: controller.signal,
        })
        vi.unstubAllGlobals()
    })
})

describe('emoticonApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls emoticon GET endpoints with proper params', () => {
        emoticonApi.getEmoticons({ page: 0, size: 20 })
        emoticonApi.getPopularEmoticons()
        emoticonApi.getPopularEmoticons('weekly')
        emoticonApi.searchAll({ keyword: 'cat' })
        emoticonApi.searchByTag('cute', { page: 1, size: 10 })
        emoticonApi.searchByKeyword('laugh', { page: 2, size: 5 })
        emoticonApi.getMyEmoticons({ page: 0, size: 10 })
        emoticonApi.getEmoticon(99)
        emoticonApi.getPurchasedEmoticons({ page: 0, size: 5 })
        emoticonApi.checkPurchaseStatus(99)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/emoticons', { params: { page: 0, size: 20 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/emoticons/popular', { params: { period: 'daily' } })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/emoticons/popular', { params: { period: 'weekly' } })
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/emoticons/search/all', { params: { keyword: 'cat' } })
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/emoticons/search/tag', { params: { tag: 'cute', page: 1, size: 10 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(6, '/emoticons/search', { params: { keyword: 'laugh', page: 2, size: 5 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(7, '/emoticons/my', { params: { page: 0, size: 10 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(8, '/emoticons/99')
        expect(apiMock.get).toHaveBeenNthCalledWith(9, '/emoticons/purchased', { params: { page: 0, size: 5 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(10, '/emoticons/99/purchased')
    })

    it('calls emoticon mutation endpoints with proper payload', () => {
        const createData = { name: 'new-pack', description: 'desc' }
        const updateData = { name: 'updated-pack' }

        emoticonApi.createEmoticon(createData as never)
        emoticonApi.updateEmoticon(9, updateData as never)
        emoticonApi.toggleVisibility(9)
        emoticonApi.deleteEmoticon(9)
        emoticonApi.addImage(9, 77)
        emoticonApi.deleteImage(55)
        emoticonApi.purchaseEmoticon(9)

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/emoticons', createData)
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/emoticons/9', updateData)
        expect(apiMock.patch).toHaveBeenNthCalledWith(1, '/emoticons/9/visibility')
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/emoticons/9')
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/emoticons/9/images', { fileId: 77 })
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/emoticons/images/55')
        expect(apiMock.post).toHaveBeenNthCalledWith(3, '/emoticons/9/purchase')
    })

    it('unwraps emoticon response helpers without changing endpoint calls', async () => {
        const listPage = { content: [{ emoticonId: 1, name: 'cat' }], totalPages: 1, totalElements: 1 }
        const detail = { emoticonId: 1, name: 'cat', isActive: true }
        const purchaseStatus = { purchased: false, price: 100 }

        apiMock.get
            .mockResolvedValueOnce({ data: { success: true, data: listPage } })
            .mockResolvedValueOnce({ data: { success: true, data: detail } })
            .mockResolvedValueOnce({ data: { success: true, data: purchaseStatus } })
        apiMock.patch.mockResolvedValueOnce({ data: { success: true, data: { ...detail, isActive: false } } })

        await expect(emoticonApi.searchAllData({ keyword: 'cat' })).resolves.toBe(listPage)
        await expect(emoticonApi.getEmoticonData(1)).resolves.toBe(detail)
        await expect(emoticonApi.checkPurchaseStatusData(1)).resolves.toBe(purchaseStatus)
        await expect(emoticonApi.toggleVisibilityData(1)).resolves.toEqual({ ...detail, isActive: false })

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/emoticons/search/all', { params: { keyword: 'cat' } })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/emoticons/1')
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/emoticons/1/purchased')
        expect(apiMock.patch).toHaveBeenCalledWith('/emoticons/1/visibility')
    })
})
