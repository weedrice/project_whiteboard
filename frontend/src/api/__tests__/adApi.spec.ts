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

  it('calls ad endpoints with existing paths and unwraps envelopes', async () => {
    const ad = { adId: 11, title: 'Ad', imageUrl: null, targetUrl: 'https://example.com' }
    apiMock.get.mockResolvedValueOnce({ data: { success: true, data: ad } })
    apiMock.post
      .mockResolvedValueOnce({ data: { success: true } })
      .mockResolvedValueOnce({ data: { success: true, data: 'https://example.com' } })

    await expect(adApi.getAd('SIDEBAR')).resolves.toEqual(ad)
    await adApi.recordImpression(11)
    await expect(adApi.recordClick(11)).resolves.toBe('https://example.com')

    expect(apiMock.get).toHaveBeenCalledWith('/ads', {
      params: { placement: 'SIDEBAR' },
    })
    expect(apiMock.post).toHaveBeenNthCalledWith(1, '/ads/11/impression')
    expect(apiMock.post).toHaveBeenNthCalledWith(2, '/ads/11/click')
  })
})
