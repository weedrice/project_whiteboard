import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../index', () => ({
  default: apiMock,
}))

import { adApi } from '../ad'

describe('adApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls ad endpoints with existing paths and payloads', () => {
    adApi.getAd('SIDEBAR')
    adApi.recordImpression(11)
    adApi.recordClick(11)

    expect(apiMock.get).toHaveBeenCalledWith('/ads', {
      params: { placement: 'SIDEBAR' },
    })
    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/ads/11/impression')
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/ads/11/click')
  })
})
