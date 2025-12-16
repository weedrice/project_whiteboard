import api from './index'
import type { ApiResponse, PageResponse, Report } from '@/types'

interface PaginationParams {
    page?: number
    size?: number
}

export const reportApi = {
    reportUser: (targetUserId: string | number, reason: string, link: string) => api.post<ApiResponse<void>>(`/reports/users`, { targetUserId, reason, link }),
    reportPost: (targetPostId: string | number, reason: string) => api.post<ApiResponse<void>>(`/reports/posts`, { targetPostId, reason }),
    reportComment: (targetCommentId: string | number, reason: string) => api.post<ApiResponse<void>>(`/reports/comments`, { targetCommentId, reason }),
    getMyReports: (params: PaginationParams) => api.get<ApiResponse<PageResponse<Report>>>('/reports/me', { params }),
}

