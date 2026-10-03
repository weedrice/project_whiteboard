import { beforeEach, describe, expect, it, vi } from 'vitest'
import { commonCodeApi } from '@/api/commonCode'

const apiMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))

vi.mock('@/api', () => ({ default: apiMock }))

describe('commonCodeApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('maps master and detail CRUD endpoints', () => {
    const code = { typeCode: 'REPORT TYPE', typeName: 'Reports', description: null }
    const detail = { codeValue: 'SPAM', codeName: 'Spam', sortOrder: 1, isActive: true }

    commonCodeApi.getAll()
    commonCodeApi.create(code)
    commonCodeApi.update('REPORT TYPE', code)
    commonCodeApi.getDetails('REPORT TYPE')
    commonCodeApi.getAllDetails('REPORT TYPE')
    commonCodeApi.createDetail('REPORT TYPE', detail)
    commonCodeApi.updateDetail(3, detail)
    commonCodeApi.deleteDetail(3)

    expect(apiMock.get).toHaveBeenNthCalledWith(1, '/common-codes', undefined)
    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/common-codes', code, undefined)
    expect(apiMock.put).toHaveBeenNthCalledWith(1, '/common-codes/REPORT%20TYPE', code, undefined)
    expect(apiMock.get).toHaveBeenNthCalledWith(2, '/common-codes/REPORT%20TYPE/details', undefined)
    expect(apiMock.get).toHaveBeenNthCalledWith(3, '/common-codes/REPORT%20TYPE/details/all', undefined)
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/common-codes/REPORT%20TYPE/details', detail, undefined)
    expect(apiMock.put).toHaveBeenNthCalledWith(2, '/common-codes/details/3', detail, undefined)
    expect(apiMock.delete).toHaveBeenCalledWith('/common-codes/details/3', undefined)
  })

  it('forwards the original session and cancellation config for every write', () => {
    const config = {
      expectedSession: { sessionGeneration: 4, userId: 7 },
      signal: new AbortController().signal,
      headers: { 'X-Test': 'common-code' },
    }
    const code = { typeCode: 'REPORT TYPE', typeName: 'Reports', description: null }
    const detail = { codeValue: 'SPAM', codeName: 'Spam', sortOrder: 1, isActive: true }

    commonCodeApi.create(code, config)
    commonCodeApi.update('REPORT TYPE', code, config)
    commonCodeApi.createDetail('REPORT TYPE', detail, config)
    commonCodeApi.updateDetail(3, detail, config)
    commonCodeApi.deleteDetail(3, config)

    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/common-codes', code, config)
    expect(apiMock.put).toHaveBeenNthCalledWith(1, '/common-codes/REPORT%20TYPE', code, config)
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/common-codes/REPORT%20TYPE/details', detail, config)
    expect(apiMock.put).toHaveBeenNthCalledWith(2, '/common-codes/details/3', detail, config)
    expect(apiMock.delete).toHaveBeenCalledWith('/common-codes/details/3', config)
    for (const call of [...apiMock.post.mock.calls, ...apiMock.put.mock.calls, ...apiMock.delete.mock.calls]) {
      expect(call.at(-1)).toBe(config)
    }
  })
})
