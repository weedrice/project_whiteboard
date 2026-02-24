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
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/boards/general')
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/boards/general/categories', categoryData)
        expect(apiMock.put).toHaveBeenNthCalledWith(3, '/boards/categories/12', categoryUpdateData)
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/boards/categories/12')
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/boards/general/notices')
        expect(apiMock.post).toHaveBeenNthCalledWith(3, '/boards/general/subscribe')
        expect(apiMock.delete).toHaveBeenNthCalledWith(3, '/boards/general/subscribe')
        expect(apiMock.put).toHaveBeenNthCalledWith(4, '/boards/subscriptions/order', ['general', 'tech'])
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
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/posts/trending', { params: { page: 0, size: 10 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/posts/trending', { params: { page: 2, size: 5 } })
        expect(apiMock.post).toHaveBeenNthCalledWith(5, '/reports/posts', reportData)
    })
})

describe('searchApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls search endpoints with params', () => {
        const params = { keyword: 'vue', page: 1, size: 20, type: 'post' }

        searchApi.search(params as never)
        searchApi.searchPosts(params as never)
        searchApi.getPopularKeywords()

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/search', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/search/posts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/search/popular-keywords')
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
        emoticonApi.addImage(9, 'https://example.com/image.png')
        emoticonApi.deleteImage(55)
        emoticonApi.purchaseEmoticon(9)

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/emoticons', createData)
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/emoticons/9', updateData)
        expect(apiMock.patch).toHaveBeenNthCalledWith(1, '/emoticons/9/visibility')
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/emoticons/9')
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/emoticons/9/images', { imageUrl: 'https://example.com/image.png' })
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/emoticons/images/55')
        expect(apiMock.post).toHaveBeenNthCalledWith(3, '/emoticons/9/purchase')
    })
})
