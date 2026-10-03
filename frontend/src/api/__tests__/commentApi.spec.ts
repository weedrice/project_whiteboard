import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

import { commentApi } from '../comment'

describe('commentApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('preserves session intent, cancellation and request options across mutation methods', () => {
        const controller = new AbortController()
        const config = {
            expectedSession: { sessionGeneration: 7, userId: 11 },
            signal: controller.signal,
            skipGlobalErrorHandler: true,
            headers: { 'X-Request-ID': 'comment-mutation' },
        }

        commentApi.createComment(3, { content: 'hello' }, config)
        commentApi.updateComment(11, { content: 'updated' }, config)
        commentApi.deleteComment(11, config)
        commentApi.likeComment(11, config)
        commentApi.unlikeComment(11, config)

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/posts/3/comments', { content: 'hello' }, config)
        expect(apiMock.put).toHaveBeenCalledWith('/comments/11', { content: 'updated' }, config)
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/comments/11', config)
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/comments/11/like', undefined, config)
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/comments/11/like', config)
    })

    it('calls comment endpoints with expected paths', () => {
        commentApi.getComments(3, { page: 0, size: 20 })
        commentApi.getReplies(11, { page: 0, size: 20 })
        commentApi.createComment(3, { content: 'hello' })
        commentApi.updateComment(11, { content: 'updated' })
        commentApi.deleteComment(11)
        commentApi.likeComment(11)
        commentApi.unlikeComment(11)
        commentApi.getComment(11)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/posts/3/comments', { params: { page: 0, size: 20 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/comments/11/replies', { params: { page: 0, size: 20 } })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/posts/3/comments', { content: 'hello' }, undefined)
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/comments/11/like', undefined, undefined)
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/comments/11', { content: 'updated' }, undefined)
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/comments/11', undefined)
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/comments/11/like', undefined)
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/comments/11')
    })
})
