import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, IpBlock, ModerationAuditLog, ModerationAuditSearchParams, PageResponse, Report } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import type {
    IpBlockData,
    PaginationParams,
    ReportResolveData,
} from '@/api/adminTypes'

export const adminModerationApi = {
    getIpBlocks(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<IpBlock>>>('/admin/ip-blocks', { ...config, params })
    },
    blockIp(data: IpBlockData) {
        return api.post<ApiResponse<IpBlock>>('/admin/ip-blocks', data)
    },
    unblockIp(ipAddress: string) {
        return api.delete<ApiResponse<void>>(`/admin/ip-blocks/${encodePathSegment(ipAddress)}`)
    },
    getReports(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<Report>>>('/admin/reports', { ...config, params })
    },
    resolveReport(reportId: string | number, data: ReportResolveData) {
        return api.put<ApiResponse<void>>(`/admin/reports/${encodePathSegment(reportId)}`, data)
    },
    getModerationAudits(params: ModerationAuditSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<ModerationAuditLog>>>('/admin/moderation-audits', { ...config, params })
    },
}
