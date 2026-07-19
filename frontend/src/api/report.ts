import api from './index'
import type { ApiResponse, MyReport, ReportReasonType } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import type { AxiosRequestConfig } from 'axios'

interface PaginationParams {
    page?: number
    size?: number
}

export const reportApi = {
    reportUser: (
        targetUserId: string | number,
        reason: string,
        link: string,
        config?: AxiosRequestConfig,
        reasonType?: ReportReasonType,
    ) => api.post<ApiResponse<number>>('/reports/users', {
        targetUserId,
        reason,
        link,
        ...(reasonType ? { reasonType } : {}),
    }, config),
    reportPost: (targetPostId: string | number, reason: string, reasonType?: ReportReasonType) =>
        api.post<ApiResponse<number>>('/reports/posts', {
            targetPostId,
            reason,
            ...(reasonType ? { reasonType } : {}),
        }),
    reportComment: (
        targetCommentId: string | number,
        reason: string,
        reasonType?: ReportReasonType,
        config?: AxiosRequestConfig,
    ) => {
        const payload = {
            targetCommentId,
            reason,
            ...(reasonType ? { reasonType } : {}),
        }
        return config
            ? api.post<ApiResponse<number>>('/reports/comments', payload, config)
            : api.post<ApiResponse<number>>('/reports/comments', payload)
    },
    getMyReports: (params: PaginationParams, config?: AxiosRequestConfig) => api.get<ApiResponse<PageResponseRaw<MyReport>>>('/reports/me', { ...config, params }),
}

