import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    ErrorLogDetail,
    ErrorLogListItem,
    ErrorLogSearchParams,
    ErrorLogStats,
    PageResponse,
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import { getWithOptionalConfig } from '@/api/adminTypes'

export const adminErrorLogApi = {
    getErrorLogs(params: ErrorLogSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<ErrorLogListItem>>>('/admin/error-logs', { ...config, params })
    },
    getErrorLog(errorLogId: number, config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<ErrorLogDetail>>(`/admin/error-logs/${encodePathSegment(errorLogId)}`, config)
    },
    resolveErrorLog(errorLogId: number, data?: { memo?: string }) {
        return api.put<ApiResponse<void>>(`/admin/error-logs/${encodePathSegment(errorLogId)}/resolve`, data)
    },
    getErrorLogStats(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<ErrorLogStats>>('/admin/error-logs/stats', config)
    },
}
