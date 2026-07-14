import type { AxiosRequestConfig } from 'axios'
import api from './index'
import type { ApiResponse } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'

export interface ShopItem {
  itemId: number
  itemName: string
  description: string | null
  price: number
  itemType: string
}

export interface PurchaseHistoryItem {
  itemId: number
  itemName: string
  imageUrl: string | null
}

export interface PurchaseHistory {
  purchaseId: number
  item: PurchaseHistoryItem
  price: number
  purchasedAt: string
}

export interface ShopPageParams {
  page?: number
  size?: number
}

export interface ShopItemPageParams extends ShopPageParams {
  itemType?: string
}

export const shopApi = {
  getItems(params: ShopItemPageParams = {}, config?: AxiosRequestConfig) {
    return api.get<ApiResponse<PageResponseRaw<ShopItem>>>('/shop/items', {
      ...config,
      params: { ...config?.params, ...params },
    })
  },

  purchaseItem(itemId: number, config?: AxiosRequestConfig) {
    return api.post<ApiResponse<number>>(
      `/shop/items/${encodePathSegment(itemId)}/purchase`,
      undefined,
      config,
    )
  },

  getMyPurchases(params: ShopPageParams = {}, config?: AxiosRequestConfig) {
    return api.get<ApiResponse<PageResponseRaw<PurchaseHistory>>>('/shop/me/purchases', {
      ...config,
      params: { ...config?.params, ...params },
    })
  },
}
