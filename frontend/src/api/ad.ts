import type { AxiosRequestConfig } from 'axios'
import api from './index'
import { unwrapApiData } from '@/api/response'
import type { ApiResponse } from '@/types'

export interface Ad {
    adId: number | null
    title: string
    imageUrl: string | null
    targetUrl: string | null
    placement?: string
}

export const adApi = {
    async getAd(placement: string, config?: AxiosRequestConfig): Promise<Ad | null> {
        const { data } = await api.get<ApiResponse<Ad>>('/ads', {
            ...config,
            params: { placement },
        })
        return data.success ? unwrapApiData(data) : null
    },

    async recordImpression(adId: number, config?: AxiosRequestConfig): Promise<void> {
        await api.post<ApiResponse<void>>(`/ads/${adId}/impression`, undefined, config)
    },

    async recordClick(adId: number): Promise<string | null> {
        const { data } = await api.post<ApiResponse<string>>(`/ads/${adId}/click`)
        const targetUrl = unwrapApiData(data)
        return data.success && targetUrl ? targetUrl : null
    },
}
