import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, DashboardStats, DeepDashboardStats, GlobalConfig } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import { getWithOptionalConfig, type ConfigCreateData } from '@/api/adminTypes'

export const adminSystemApi = {
    getConfigs(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<GlobalConfig[]>>('/admin/configs', config)
    },
    updateConfig(key: string, value: string, description?: string) {
        return api.put<ApiResponse<GlobalConfig>>(`/admin/configs/${encodePathSegment(key)}`, { value, description })
    },
    createConfig(data: ConfigCreateData) {
        return api.post<ApiResponse<GlobalConfig>>('/admin/configs', data)
    },
    deleteConfig(key: string) {
        return api.delete<ApiResponse<void>>(`/admin/configs/${encodePathSegment(key)}`)
    },
    getDashboardStats(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<DashboardStats>>('/admin/stats', config)
    },
    getDeepDashboardStats(days: 30 | 90, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<DeepDashboardStats>>('/admin/stats/deep', { ...config, params: { ...config?.params, days } })
    },
}
