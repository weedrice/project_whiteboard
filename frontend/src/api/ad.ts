import api from './index'
import type { ApiResponse } from '@/types'

export interface Ad {
    adId: number | null
    title: string
    imageUrl: string | null
    targetUrl: string | null
    placement?: string
}

export const adApi = {
    async getAd(placement: string): Promise<Ad | null> {
        const { data } = await api.get<ApiResponse<Ad>>('/ads', {
            params: { placement },
        })
        return data.success ? data.data : null
    },

    async recordImpression(adId: number): Promise<void> {
        await api.post<ApiResponse<void>>(`/ads/${adId}/impression`)
    },

    async recordClick(adId: number): Promise<string | null> {
        const { data } = await api.post<ApiResponse<string>>(`/ads/${adId}/click`)
        return data.success && data.data ? data.data : null
    },
}
