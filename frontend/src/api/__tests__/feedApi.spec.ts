import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/api', () => ({
  default: apiMock,
}))

import { feedApi } from '../feed'
import { axiosApiResponse, apiEmptySuccess } from '@/test/factories'

describe('feedApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.get.mockResolvedValue(axiosApiResponse(apiEmptySuccess()))
  })

  it('requests the authenticated user feed with pagination and request options', () => {
    const signal = new AbortController().signal

    feedApi.getMyFeeds(2, 20, {
      signal,
      params: { trace: 'feed-test' },
    })

    expect(apiMock.get).toHaveBeenCalledWith('/users/me/feeds', {
      signal,
      params: {
        page: 2,
        size: 20,
        trace: 'feed-test',
      },
    })
  })
})
