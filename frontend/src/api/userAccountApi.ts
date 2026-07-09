import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, LoginHistory, MentionCandidate, PageResponse, PublicUserProfile, User, UserSession, UserSettings } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export interface UserUpdatePayload {
    displayName?: string
    profileImageId?: number | null
    removeProfileImage?: boolean
}

export type NotificationSettingType = 'LIKE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'MESSAGE' | 'SYSTEM' | 'SANCTION' | 'KEYWORD' | 'BADGE'

export interface NotificationSettingsPayload {
    notificationType: NotificationSettingType
    isEnabled: boolean
}

export interface NotificationSettingsBulkPayload {
    settings: NotificationSettingsPayload[]
}

export interface PushSubscriptionPayload {
    endpoint: string
    keys: {
        p256dh: string
        auth: string
    }
    userAgent?: string
}

export interface PushSubscriptionResponse {
    subscriptionId: number
    endpoint: string
}

export interface PushPublicKeyResponse {
    publicKey: string
    enabled: boolean
}

export interface KeywordSubscriptionPayload {
    keyword: string
}

export interface KeywordSubscriptionResponse {
    subscriptionId: number
    keyword: string
    createdAt: string
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
    getMySessions(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<UserSession[]>>('/users/me/sessions', config)
            : api.get<ApiResponse<UserSession[]>>('/users/me/sessions')
    },
    revokeMySession(sessionId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/me/sessions/${encodePathSegment(sessionId)}`)
    },
    revokeOtherSessions() {
        return api.delete<ApiResponse<void>>('/users/me/sessions')
    },
    getMyLoginHistory(params?: { page?: number, size?: number }, config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<PageResponse<LoginHistory>>>('/users/me/login-history', { ...config, params })
            : api.get<ApiResponse<PageResponse<LoginHistory>>>('/users/me/login-history', { params })
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
    completeOnboarding() {
        return api.put<ApiResponse<UserSettings>>('/users/me/onboarding-complete')
    },
    createPushSubscription(data: PushSubscriptionPayload) {
        return api.post<ApiResponse<PushSubscriptionResponse>>('/users/me/push-subscriptions', data)
    },
    deletePushSubscription(data: PushSubscriptionPayload) {
        return api.delete<ApiResponse<void>>('/users/me/push-subscriptions', { data })
    },
    getPushPublicKey(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<PushPublicKeyResponse>>('/push/public-key', config)
            : api.get<ApiResponse<PushPublicKeyResponse>>('/push/public-key')
    },
    getKeywordSubscriptions(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<KeywordSubscriptionResponse[]>>('/users/me/keyword-subscriptions', config)
            : api.get<ApiResponse<KeywordSubscriptionResponse[]>>('/users/me/keyword-subscriptions')
    },
    createKeywordSubscription(data: KeywordSubscriptionPayload) {
        return api.post<ApiResponse<KeywordSubscriptionResponse>>('/users/me/keyword-subscriptions', data)
    },
    deleteKeywordSubscription(data: KeywordSubscriptionPayload) {
        return api.delete<ApiResponse<void>>('/users/me/keyword-subscriptions', { data })
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
