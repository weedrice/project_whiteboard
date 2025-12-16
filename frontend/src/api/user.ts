import api from '@/api'
import type { ApiResponse, PageResponse, User, UserSummary, UserSettings, PostSummary, Comment, Board } from '@/types'

export interface UserProfile {
    userId: number;
    displayName: string;
    email: string;
    profileImageUrl?: string;
    role: 'USER' | 'ADMIN' | 'SUPER_ADMIN';
    createdAt: string;
}

export interface UserUpdatePayload {
    displayName?: string;
    profileImageUrl?: string;
    profileImageId?: number | null;
}

interface PaginationParams {
    page?: number
    size?: number
}

export interface NotificationSettingsPayload {
    notificationType: string
    isEnabled: boolean
}

export const userApi = {
    getMyProfile() {
        return api.get<ApiResponse<User>>('/users/me')
    },
    getUserProfile(userId: string | number) {
        return api.get<ApiResponse<User>>(`/users/${userId}`)
    },
    updateMyProfile(data: UserUpdatePayload) {
        return api.put<ApiResponse<User>>('/users/me', data)
    },
    updatePassword(currentPassword: string, newPassword: string) {
        return api.put<ApiResponse<void>>('/users/me/password', { currentPassword, newPassword })
    },
    deleteAccount(password: string) {
        return api.delete<ApiResponse<void>>('/users/me', { data: { password } }) // DELETE usually sends data in config
    },
    // Settings
    getUserSettings() {
        return api.get<ApiResponse<UserSettings>>('/users/me/settings')
    },
    updateUserSettings(data: Partial<UserSettings>) {
        return api.put<ApiResponse<UserSettings>>('/users/me/settings', data)
    },
    getNotificationSettings() {
        return api.get<ApiResponse<NotificationSettingsPayload>>('/users/me/notification-settings')
    },
    updateNotificationSettings(data: NotificationSettingsPayload) {
        return api.put<ApiResponse<NotificationSettingsPayload>>('/users/me/notification-settings', data)
    },
    // Blocks
    blockUser(userId: string | number) {
        return api.post<ApiResponse<void>>(`/users/${userId}/block`)
    },
    unblockUser(userId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/${userId}/block`)
    },
    getBlockList() {
        return api.get<ApiResponse<UserSummary[]>>('/users/me/blocks')
    },
    // Activity
    getMyPosts(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/posts', { params })
    },
    getMyComments(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<Comment>>>('/users/me/comments', { params })
    },
    getMyScraps(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/scraps', { params })
    },
    getMyDrafts(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/drafts', { params })
    },
    getRecentlyViewedPosts(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/history/views', { params })
    },
    getMySubscriptions(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<Board>>>('/users/me/subscriptions', { params })
    }
}

