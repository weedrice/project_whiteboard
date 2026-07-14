import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import { unwrapAxiosApiPageData } from '@/api/response'
import type { ApiResponse } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../index', () => ({ default: apiMock }))

import { shopApi, type PurchaseHistory, type ShopItem } from '../shop'

describe('shopApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls the existing shop endpoints without changing their payload contract', () => {
    shopApi.getItems({ page: 1, size: 12, itemType: 'EMOTICON' })
    shopApi.purchaseItem(17)
    shopApi.getMyPurchases({ page: 2, size: 15 })

    expect(apiMock.get).toHaveBeenNthCalledWith(1, '/shop/items', {
      params: { page: 1, size: 12, itemType: 'EMOTICON' },
    })
    expect(apiMock.post).toHaveBeenCalledWith('/shop/items/17/purchase', undefined, undefined)
    expect(apiMock.get).toHaveBeenNthCalledWith(2, '/shop/me/purchases', {
      params: { page: 2, size: 15 },
    })
  })

  it('normalizes the backend shop page fields', () => {
    const response = {
      data: {
        success: true,
        data: {
          content: [{
            itemId: 1,
            itemName: 'Novi pack',
            description: 'Pack',
            price: 100,
            itemType: 'EMOTICON',
          }],
          page: 1,
          size: 12,
          totalElements: 25,
          totalPages: 3,
          hasNext: true,
          hasPrevious: true,
        },
      },
    } as AxiosResponse<ApiResponse<PageResponseRaw<ShopItem>>>

    expect(unwrapAxiosApiPageData(response)).toMatchObject({
      number: 1,
      first: false,
      last: false,
      empty: false,
    })
  })

  it('normalizes the backend first shop page without producing an invalid page number', () => {
    const response = {
      data: {
        success: true,
        data: {
          content: [],
          page: 0,
          size: 12,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrevious: false,
        },
      },
    } as AxiosResponse<ApiResponse<PageResponseRaw<ShopItem>>>

    const page = unwrapAxiosApiPageData(response)
    expect(page).toMatchObject({ number: 0, first: true, last: true, empty: true })
    expect(Number.isNaN(page.number)).toBe(false)
  })

  it('keeps the purchase history wire fields and normalizes its last page', () => {
    const response = {
      data: {
        success: true,
        data: {
          content: [{
            purchaseId: 8,
            item: { itemId: 1, itemName: 'Novi pack', imageUrl: null },
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
    } as AxiosResponse<ApiResponse<PageResponseRaw<PurchaseHistory>>>

    const page = unwrapAxiosApiPageData(response)
    expect(page).toMatchObject({ number: 2, first: false, last: true })
    expect(page.content[0]?.item.imageUrl).toBeNull()
  })
})
