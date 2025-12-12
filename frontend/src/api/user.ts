import api from '@/api'
import type { UserSettings } from '@/types'

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

interface NotificationSettingsPayload {
    notificationType: string
    isEnabled: boolean
}

export const userApi = {
    getMyProfile() {
        return api.get('/users/me')
    },
    getUserProfile(userId: string | number) {
        return api.get(`/users/${userId}`)
    },
    updateMyProfile(data: UserUpdatePayload) {
        return api.put('/users/me', data)
    },
    updatePassword(currentPassword: string, newPassword: string) {
        return api.put('/users/me/password', { currentPassword, newPassword })
    },
    deleteAccount(password: string) {
        return api.delete('/users/me', { data: { password } }) // DELETE usually sends data in config
    },
    // Settings
    getUserSettings() {
        return api.get('/users/me/settings')
    },
    updateUserSettings(data: Partial<UserSettings>) {
        return api.put('/users/me/settings', data)
    },
    getNotificationSettings() {
        return api.get('/users/me/notification-settings')
    },
    updateNotificationSettings(data: NotificationSettingsPayload) {
        return api.put('/users/me/notification-settings', data)
    },
    // Blocks
    blockUser(userId: string | number) {
        return api.post(`/users/${userId}/block`)
    },
    unblockUser(userId: string | number) {
        return api.delete(`/users/${userId}/block`)
    },
    getBlockList() {
        return api.get('/users/me/blocks')
    },
    // Activity
    getMyPosts(params: PaginationParams) {
        return api.get('/users/me/posts', { params })
    },
    getMyComments(params: PaginationParams) {
        return api.get('/users/me/comments', { params })
    },
    getMyScraps(params: PaginationParams) {
        return api.get('/users/me/scraps', { params })
    },
    getMyDrafts(params: PaginationParams) {
        return api.get('/users/me/drafts', { params })
    },
    getRecentlyViewedPosts(params: PaginationParams) {
        return api.get('/users/me/history/views', { params })
    },
    getMySubscriptions(params: PaginationParams) {
        return api.get('/users/me/subscriptions', { params })
    }
}

