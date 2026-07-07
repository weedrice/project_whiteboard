import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, MentionCandidate, PublicUserProfile, User, UserSettings } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export interface UserUpdatePayload {
    displayName?: string
    profileImageId?: number | null
}

export type NotificationSettingType = 'LIKE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'SYSTEM' | 'SANCTION'

export interface NotificationSettingsPayload {
    notificationType: NotificationSettingType
    isEnabled: boolean
}

export interface NotificationSettingsBulkPayload {
    settings: NotificationSettingsPayload[]
}

export const userAccountApi = {
    getMyProfile(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<User>>('/users/me', config)
            : api.get<ApiResponse<User>>('/users/me')
    },
    getUserProfile(userId: string | number, config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<PublicUserProfile>>(`/users/${encodePathSegment(userId)}`, config)
            : api.get<ApiResponse<PublicUserProfile>>(`/users/${encodePathSegment(userId)}`)
    },
    getMentionCandidates(keyword: string, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<MentionCandidate[]>>('/users/mention-candidates', {
            ...config,
            params: { keyword },
        })
    },
    updateMyProfile(data: UserUpdatePayload) {
        return api.put<ApiResponse<User>>('/users/me', data)
    },
    updatePassword(currentPassword: string, newPassword: string) {
        return api.put<ApiResponse<void>>('/users/me/password', { currentPassword, newPassword })
    },
    deleteAccount(password: string) {
        return api.delete<ApiResponse<void>>('/users/me', { data: { password } })
    },
    verifyEmail(payload: { email: string, verificationTicket: string }) {
        return api.post<ApiResponse<void>>('/users/me/email-verification', payload)
    },
    getUserSettings(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<UserSettings>>('/users/me/settings', config)
            : api.get<ApiResponse<UserSettings>>('/users/me/settings')
    },
    updateUserSettings(data: Partial<UserSettings>) {
        return api.put<ApiResponse<UserSettings>>('/users/me/settings', data)
    },
    getNotificationSettings(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings', config)
            : api.get<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings')
    },
    updateNotificationSettingsBulk(data: NotificationSettingsBulkPayload) {
        return api.put<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings/bulk', data)
    },
}
