import type { AxiosRequestConfig } from 'axios'
import api from '@/api'
import type { ApiResponse, AdminShopItem, AdminShopItemSearchParams } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'

export interface AdminShopItemSaleStatusData {
    saleEnabled: boolean
    reason: string
}

export const adminShopApi = {
    getShopItems(params: AdminShopItemSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<AdminShopItem>>>('/admin/shop/items', {
            ...config,
            params: { ...config?.params, ...params },
        })
    },

    updateShopItemSaleStatus(itemId: number, data: AdminShopItemSaleStatusData) {
        return api.put<ApiResponse<AdminShopItem>>(
            `/admin/shop/items/${encodePathSegment(itemId)}/sale-status`,
            data,
        )
    },
}
