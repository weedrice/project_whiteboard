import api from './index'
import type { ApiResponse, ConfigEntry, GlobalConfig } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export const configApi = {
    getConfig(key: string) {
        return api.get<ApiResponse<ConfigEntry>>(`/configs/${encodePathSegment(key)}`)
    },
    getConfigs() {
        return api.get<ApiResponse<GlobalConfig[]>>('/admin/configs')
    },
    getPublicConfigs() {
        return api.get<ApiResponse<ConfigEntry[]>>('/configs/public')
    }
}

