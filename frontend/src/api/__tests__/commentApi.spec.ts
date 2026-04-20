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

    it('calls comment endpoints with expected paths', () => {
        commentApi.getComments(3, { page: 0, size: 20 })
        commentApi.getReplies(11, { page: 0, size: 20 })
        commentApi.createComment(3, { content: 'hello' })
        commentApi.updateComment(11, { content: 'updated' })
        commentApi.deleteComment(11)
        commentApi.getComment(11)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/posts/3/comments', { params: { page: 0, size: 20 } })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/comments/11/replies', { params: { page: 0, size: 20 } })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/posts/3/comments', { content: 'hello' })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/comments/11', { content: 'updated' })
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/comments/11')
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/comments/11')
    })
})
