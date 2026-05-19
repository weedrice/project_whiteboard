import api from './index'
import type { ApiResponse } from '@/types'

export interface Ad {
  adId: number | null
  title: string
  imageUrl: string | null
  targetUrl: string | null
}

export const adApi = {
  getAd: (placement: string) => api.get<ApiResponse<Ad>>('/ads', {
    params: { placement },
  }),

  recordImpression: (adId: number) => api.post<ApiResponse<void>>(`/ads/${adId}/impression`),

  recordClick: (adId: number) => api.post<ApiResponse<string>>(`/ads/${adId}/click`),
}
