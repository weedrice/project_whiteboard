import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosRequestConfig } from 'axios'

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
        const config: AxiosRequestConfig = { skipGlobalErrorHandler: true }

        messageApi.sendMessage(2, 'hello', config)

        expect(apiMock.post).toHaveBeenCalledWith('/messages', { receiverId: 2, content: 'hello' }, config)
    })

    it('calls mailbox list endpoints with pagination params and config', () => {
        const config: AxiosRequestConfig = { signal: new AbortController().signal }
        const params = { page: 1, size: 15 }

        messageApi.getReceivedMessages(params, config)
        messageApi.getSentMessages(params, config)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/messages/received', { ...config, params })
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/messages/sent', { ...config, params })
    })

    it('calls message detail and read endpoints separately', () => {
        const config: AxiosRequestConfig = { skipGlobalErrorHandler: true }

        messageApi.getMessage(9, config)
        messageApi.markAsRead(9, config)
        messageApi.deleteMessage(9)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/messages/9', config)
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/messages/9/read', null, config)
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/messages/9')
    })
})
