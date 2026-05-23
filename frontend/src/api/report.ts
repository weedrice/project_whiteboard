import api from './index'
import type { ApiResponse, MyReport, PageResponse } from '@/types'

interface PaginationParams {
    page?: number
    size?: number
}

export const reportApi = {
    reportUser: (targetUserId: string | number, reason: string, link: string) => api.post<ApiResponse<number>>(`/reports/users`, { targetUserId, reason, link }),
    reportPost: (targetPostId: string | number, reason: string) => api.post<ApiResponse<number>>(`/reports/posts`, { targetPostId, reason }),
    reportComment: (targetCommentId: string | number, reason: string) => api.post<ApiResponse<number>>(`/reports/comments`, { targetCommentId, reason }),
    getMyReports: (params: PaginationParams) => api.get<ApiResponse<PageResponse<MyReport>>>('/reports/me', { params }),
}

