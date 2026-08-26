import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../index', () => ({ default: apiMock }))

import { inquiryApi } from '../inquiry'

describe('inquiryApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses inquiry-owned user endpoints and payloads', () => {
    const create = { category: 'ACCOUNT' as const, title: 'Help', content: 'Locked out', fileIds: [3] }
    const message = { content: 'More details', fileIds: [4] }

    inquiryApi.create(create)
    inquiryApi.getMine({ status: 'NEW', page: 0, size: 20 })
    inquiryApi.getMineDetail(12)
    inquiryApi.addMessage(12, message)
    inquiryApi.withdraw(12)
    inquiryApi.close(12)

    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/inquiries', create)
    expect(apiMock.get).toHaveBeenNthCalledWith(1, '/inquiries', {
      params: { status: 'NEW', page: 0, size: 20 },
    })
    expect(apiMock.get).toHaveBeenNthCalledWith(2, '/inquiries/12', undefined)
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/inquiries/12/messages', message)
    expect(apiMock.post).toHaveBeenNthCalledWith(3, '/inquiries/12/withdraw')
    expect(apiMock.post).toHaveBeenNthCalledWith(4, '/inquiries/12/close')
  })

  it('uses the non-conflicting support admin namespace', () => {
    const filters = { priority: 'URGENT' as const, keyword: 'account', page: 1 }
    const message = { content: 'Resolved', fileIds: [] }

    inquiryApi.getAdminPage(filters)
    inquiryApi.getAdminDetail(21)
    inquiryApi.start(21)
    inquiryApi.reply(21, message)
    inquiryApi.addNote(21, message)
    inquiryApi.adminClose(21, 'completed')
    inquiryApi.reopen(21)

    expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/support/inquiries', { params: filters })
    expect(apiMock.get).toHaveBeenNthCalledWith(2, '/admin/support/inquiries/21', undefined)
    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/admin/support/inquiries/21/start')
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/admin/support/inquiries/21/reply', message)
    expect(apiMock.post).toHaveBeenNthCalledWith(3, '/admin/support/inquiries/21/notes', message)
    expect(apiMock.post).toHaveBeenNthCalledWith(4, '/admin/support/inquiries/21/close', { reason: 'completed' })
    expect(apiMock.post).toHaveBeenNthCalledWith(5, '/admin/support/inquiries/21/reopen')
  })
})
