import api from './index'
import type { ApiResponse, GlobalConfig } from '@/types'

export const configApi = {
    getConfig(key: string) {
        return api.get<ApiResponse<GlobalConfig>>(`/configs/${key}`)
    },
    getConfigs() {
        return api.get<ApiResponse<GlobalConfig[]>>('/configs')
    },
    getPublicConfigs() {
        return api.get<ApiResponse<Record<string, string>>>('/configs/public')
    }
}

