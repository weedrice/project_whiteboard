import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import { unwrapAxiosApiPageData } from '@/api/response'
import type { ApiResponse } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('../index', () => ({ default: apiMock }))

import { shopApi, type PurchaseHistory } from '../shop'

describe('shopApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('preserves the purchase history endpoint and pagination contract', () => {
    shopApi.getMyPurchases({ page: 2, size: 15 })

    expect(apiMock.get).toHaveBeenNthCalledWith(1, '/shop/me/purchases', {
      params: { page: 2, size: 15 },
    })
  })

  it('keeps the purchase history wire fields and normalizes its last page', () => {
    const response = {
      data: {
        success: true,
        data: {
          content: [{
            purchaseId: 8,
            item: { itemId: 1, itemType: 'EMOTICON', itemName: 'Novi pack', imageUrl: null },
            price: 100,
            purchasedAt: '2026-07-14T12:00:00',
          }],
          page: 2,
          size: 15,
          totalElements: 31,
          totalPages: 3,
          hasNext: false,
          hasPrevious: true,
        },
      },
    } as unknown as AxiosResponse<ApiResponse<PageResponseRaw<PurchaseHistory>>>

    const page = unwrapAxiosApiPageData(response)
    expect(page).toMatchObject({ number: 2, first: false, last: true })
    expect(page.content[0]?.item.imageUrl).toBeNull()
  })
})
