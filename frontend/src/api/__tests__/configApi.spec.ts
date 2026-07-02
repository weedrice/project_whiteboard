import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

import { configApi } from '../config'

describe('configApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls config endpoints with DTO-based responses', () => {
        configApi.getConfig('site.name')
        configApi.getConfigs()
        configApi.getPublicConfigs()

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/configs/site.name')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/admin/configs')
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/configs/public')
    })

    it('encodes config keys before appending them to the path', () => {
        configApi.getConfig('site/name value')

        expect(apiMock.get).toHaveBeenCalledWith('/configs/site%2Fname%20value')
    })
})
