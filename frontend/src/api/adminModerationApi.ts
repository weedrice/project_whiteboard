import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, IpBlock, ModerationAuditLog, ModerationAuditSearchParams, Report } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'
import type {
    IpBlockData,
    PaginationParams,
    ReportResolveData,
    ReportSearchParams,
} from '@/api/adminTypes'

export const adminModerationApi = {
    getIpBlocks(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<IpBlock>>>('/admin/ip-blocks', { ...config, params })
    },
    blockIp(data: IpBlockData, config?: AxiosRequestConfig) {
        return api.post<ApiResponse<IpBlock>>('/admin/ip-blocks', data, config)
    },
    unblockIp(ipAddress: string, config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<void>>(`/admin/ip-blocks/${encodePathSegment(ipAddress)}`, config)
    },
    getReports(params: ReportSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<Report>>>('/admin/reports', { ...config, params })
    },
    resolveReport(reportId: string | number, data: ReportResolveData, config?: AxiosRequestConfig) {
        return api.put<ApiResponse<Report>>(`/admin/reports/${encodePathSegment(reportId)}`, data, config)
    },
    getModerationAudits(params: ModerationAuditSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<ModerationAuditLog>>>('/admin/moderation-audits', { ...config, params })
    },
}
