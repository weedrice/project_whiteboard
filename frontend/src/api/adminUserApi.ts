import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    AdminUserCommentItem,
    AdminUserDetail,
    AdminUserPostItem,
    AdminUserSubscriptionItem,
    ApiResponse,
    SanctionData,
    SanctionHistoryItem,
    User,
} from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'
import {
    getWithOptionalConfig,
    type PaginationParams,
    type UserSearchParams,
} from '@/api/adminTypes'

export const adminUserApi = {
    getUsers(params: UserSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<User>>>('/admin/users', { ...config, params })
    },
    getUserDetail(userId: string | number, config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<AdminUserDetail>>(`/admin/users/${encodePathSegment(userId)}`, config)
    },
    getUserPosts(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<AdminUserPostItem>>>(`/admin/users/${encodePathSegment(userId)}/posts`, { ...config, params })
    },
    getUserComments(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<AdminUserCommentItem>>>(`/admin/users/${encodePathSegment(userId)}/comments`, { ...config, params })
    },
    getUserSubscriptions(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<AdminUserSubscriptionItem>>>(`/admin/users/${encodePathSegment(userId)}/subscriptions`, { ...config, params })
    },
    updateUserStatus(userId: string | number, status: string, reason: string) {
        return api.put<ApiResponse<void>>(`/admin/users/${encodePathSegment(userId)}/status`, { status, reason })
    },
    sanctionUser(data: SanctionData) {
        return api.post<ApiResponse<number>>('/admin/sanctions', data)
    },
    getUserSanctions(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<SanctionHistoryItem>>>('/admin/sanctions', {
            ...config,
            params: { ...params, userId },
        })
    },
}
