import api from './index'
import type { ApiResponse, Badge } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import type { AxiosRequestConfig } from 'axios'

export const badgeApi = {
    getUserBadges: (userId: string | number, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<Badge[]>>(`/users/${encodePathSegment(userId)}/badges`, config),

    getMyBadges: (config?: AxiosRequestConfig) =>
        api.get<ApiResponse<Badge[]>>('/users/me/badges', config),

    updateRepresentativeBadge: (badgeCode: string | null) =>
        api.put<ApiResponse<Badge | null>>('/users/me/badges/representative', { badgeCode }),
}
