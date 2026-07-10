import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    post: vi.fn(),
}))

vi.mock('@/api', () => ({
    default: apiMock,
}))

import { clientErrorLogApi, type ClientErrorLogRequest } from '../clientErrorLogApi'

describe('clientErrorLogApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('posts client errors without invoking recursive global handling or auth refresh', () => {
        const payload: ClientErrorLogRequest = {
            source: 'VUE',
            errorType: 'TypeError',
            message: 'render failed',
            pageUrl: '/boards/test',
            commitHash: 'abc123',
        }

        clientErrorLogApi.create(payload)

        expect(apiMock.post).toHaveBeenCalledWith('/logs/client', payload, {
            skipGlobalErrorHandler: true,
            skipAuthRefresh: true,
        })
    })
})
