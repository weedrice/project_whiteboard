import type { AxiosRequestConfig } from 'axios'
import api from './index'
import type { ApiResponse } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'

export interface PurchaseHistoryItem {
  itemType: string
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

export const shopApi = {
  getMyPurchases(params: ShopPageParams = {}, config?: AxiosRequestConfig) {
    return api.get<ApiResponse<PageResponseRaw<PurchaseHistory>>>('/shop/me/purchases', {
      ...config,
      params: { ...config?.params, ...params },
    })
  },
}
