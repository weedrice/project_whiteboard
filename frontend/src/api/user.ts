import api from '@/api'

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
    updateUserSettings(data: any) {
        return api.put('/users/me/settings', data)
    },
    getNotificationSettings() {
        return api.get('/users/me/notification-settings')
    },
    updateNotificationSettings(data: any) {
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
    getMyPosts(params: any) {
        return api.get('/users/me/posts', { params })
    },
    getMyComments(params: any) {
        return api.get('/users/me/comments', { params })
    },
    getMyScraps(params: any) {
        return api.get('/users/me/scraps', { params })
    },
    getMyDrafts(params: any) {
        return api.get('/users/me/drafts', { params })
    },
    getRecentlyViewedPosts(params: any) {
        return api.get('/users/me/history/views', { params })
    },
    getMySubscriptions(params: any) {
        return api.get('/users/me/subscriptions', { params })
    }
}
