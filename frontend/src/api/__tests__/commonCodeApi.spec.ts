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
    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/common-codes', code)
    expect(apiMock.put).toHaveBeenNthCalledWith(1, '/common-codes/REPORT%20TYPE', code)
    expect(apiMock.get).toHaveBeenNthCalledWith(2, '/common-codes/REPORT%20TYPE/details', undefined)
    expect(apiMock.get).toHaveBeenNthCalledWith(3, '/common-codes/REPORT%20TYPE/details/all', undefined)
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/common-codes/REPORT%20TYPE/details', detail)
    expect(apiMock.put).toHaveBeenNthCalledWith(2, '/common-codes/details/3', detail)
    expect(apiMock.delete).toHaveBeenCalledWith('/common-codes/details/3')
  })
})
