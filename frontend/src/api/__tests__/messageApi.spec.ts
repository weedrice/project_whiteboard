import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

import { messageApi } from '../message'

describe('messageApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('posts a new message with config', () => {
        const config = { skipGlobalErrorHandler: true }

        messageApi.sendMessage(2, 'hello', config as never)

        expect(apiMock.post).toHaveBeenCalledWith('/messages', { receiverId: 2, content: 'hello' }, config)
    })

    it('calls message detail and read endpoints separately', () => {
        const config = { skipGlobalErrorHandler: true }

        messageApi.getMessage(9, config as never)
        messageApi.markAsRead(9, config as never)
        messageApi.deleteMessage(9)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/messages/9', config)
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/messages/9/read', null, config)
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/messages/9')
    })
})
