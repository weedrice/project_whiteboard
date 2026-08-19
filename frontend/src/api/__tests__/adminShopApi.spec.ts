import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
}))

vi.mock('@/api', () => ({ default: apiMock }))

import { adminShopApi } from '@/api/adminShopApi'

describe('adminShopApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('queries admin shop items with filters and request cancellation', () => {
    const controller = new AbortController()

    adminShopApi.getShopItems(
      {
        page: 1,
        size: 20,
        q: 'premium',
        itemType: 'EMOTICON',
        isActive: true,
        isSaleEnabled: false,
      },
      { signal: controller.signal, params: { sort: 'itemId,desc' } },
    )

    expect(apiMock.get).toHaveBeenCalledWith('/admin/shop/items', {
      signal: controller.signal,
      params: {
        sort: 'itemId,desc',
        page: 1,
        size: 20,
        q: 'premium',
        itemType: 'EMOTICON',
        isActive: true,
        isSaleEnabled: false,
      },
    })
  })

  it('updates an item sale status with an audit reason', () => {
    adminShopApi.updateShopItemSaleStatus(17, {
      saleEnabled: false,
      reason: 'temporary review',
    })

    expect(apiMock.put).toHaveBeenCalledWith(
      '/admin/shop/items/17/sale-status',
      { saleEnabled: false, reason: 'temporary review' },
    )
  })
})
