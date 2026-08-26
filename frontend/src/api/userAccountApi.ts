import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    ActionMessageResponse,
    ApiResponse,
    LoginHistory,
    MentionCandidate,
    PublicUserProfile,
    UpdateProfileResponse,
    User,
    UserSession,
    UserSettings,
    UserSettingsUpdatePayload,
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import type { PageResponseRaw } from '@/utils/pageResponse'

export interface UserUpdatePayload {
    displayName?: string
    profileImageId?: number | null
    removeProfileImage?: boolean
}

export type NotificationSettingType = 'LIKE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'MESSAGE' | 'SYSTEM' | 'SANCTION' | 'KEYWORD' | 'BADGE' | 'INQUIRY'

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
    updateMyProfile(data: UserUpdatePayload, config?: AxiosRequestConfig) {
        return config
            ? api.put<ApiResponse<UpdateProfileResponse>>('/users/me', data, config)
            : api.put<ApiResponse<UpdateProfileResponse>>('/users/me', data)
    },
    getMySessions(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<UserSession[]>>('/users/me/sessions', config)
            : api.get<ApiResponse<UserSession[]>>('/users/me/sessions')
    },
    revokeMySession(sessionId: string | number, config?: AxiosRequestConfig) {
        return config
            ? api.delete<ApiResponse<void>>(`/users/me/sessions/${encodePathSegment(sessionId)}`, config)
            : api.delete<ApiResponse<void>>(`/users/me/sessions/${encodePathSegment(sessionId)}`)
    },
    revokeOtherSessions(config?: AxiosRequestConfig) {
        return config
            ? api.delete<ApiResponse<void>>('/users/me/sessions', config)
            : api.delete<ApiResponse<void>>('/users/me/sessions')
    },
    getMyLoginHistory(params?: { page?: number, size?: number }, config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<PageResponseRaw<LoginHistory>>>('/users/me/login-history', { ...config, params })
            : api.get<ApiResponse<PageResponseRaw<LoginHistory>>>('/users/me/login-history', { params })
    },
    updatePassword(currentPassword: string, newPassword: string) {
        return api.put<ApiResponse<ActionMessageResponse>>('/users/me/password', { currentPassword, newPassword })
    },
    deleteAccount(password: string, config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<ActionMessageResponse>>('/users/me', { ...config, data: { password } })
    },
    verifyEmail(payload: { email: string, verificationTicket: string }, config?: AxiosRequestConfig) {
        return config
            ? api.post<ApiResponse<void>>('/users/me/email-verification', payload, config)
            : api.post<ApiResponse<void>>('/users/me/email-verification', payload)
    },
    getUserSettings(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<UserSettings>>('/users/me/settings', config)
            : api.get<ApiResponse<UserSettings>>('/users/me/settings')
    },
    updateUserSettings(data: UserSettingsUpdatePayload, config?: AxiosRequestConfig) {
        return api.put<ApiResponse<UserSettings>>('/users/me/settings', data, config)
    },
    completeOnboarding(config?: AxiosRequestConfig) {
        return api.put<ApiResponse<UserSettings>>('/users/me/onboarding-complete', undefined, config)
    },
    createPushSubscription(data: PushSubscriptionPayload, config?: AxiosRequestConfig) {
        return api.post<ApiResponse<PushSubscriptionResponse>>('/users/me/push-subscriptions', data, config)
    },
    deletePushSubscription(data: PushSubscriptionPayload, config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<void>>('/users/me/push-subscriptions', { ...config, data })
    },
    deleteAllPushSubscriptions(config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<void>>('/users/me/push-subscriptions/all', config)
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
    createKeywordSubscription(data: KeywordSubscriptionPayload, config?: AxiosRequestConfig) {
        return config
            ? api.post<ApiResponse<KeywordSubscriptionResponse>>('/users/me/keyword-subscriptions', data, config)
            : api.post<ApiResponse<KeywordSubscriptionResponse>>('/users/me/keyword-subscriptions', data)
    },
    deleteKeywordSubscription(data: KeywordSubscriptionPayload, config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<void>>('/users/me/keyword-subscriptions', { ...config, data })
    },
    getNotificationSettings(config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings', config)
            : api.get<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings')
    },
    updateNotificationSettingsBulk(data: NotificationSettingsBulkPayload, config?: AxiosRequestConfig) {
        return config
            ? api.put<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings/bulk', data, config)
            : api.put<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings/bulk', data)
    },
}
