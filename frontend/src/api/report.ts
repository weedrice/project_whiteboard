import api from './index'
import type { ApiResponse, MyReport, PageResponse } from '@/types'
import type { AxiosRequestConfig } from 'axios'

interface PaginationParams {
    page?: number
    size?: number
}

export const reportApi = {
    reportUser: (targetUserId: string | number, reason: string, link: string, config?: AxiosRequestConfig) => api.post<ApiResponse<number>>(`/reports/users`, { targetUserId, reason, link }, config),
    reportPost: (targetPostId: string | number, reason: string) => api.post<ApiResponse<number>>(`/reports/posts`, { targetPostId, reason }),
    reportComment: (targetCommentId: string | number, reason: string) => api.post<ApiResponse<number>>(`/reports/comments`, { targetCommentId, reason }),
    getMyReports: (params: PaginationParams, config?: AxiosRequestConfig) => api.get<ApiResponse<PageResponse<MyReport>>>('/reports/me', { ...config, params }),
}

