import api from '@/api'

export const userApi = {
    getMyProfile() {
        return api.get('/users/me')
    },
    getUserProfile(userId) {
        return api.get(`/users/${userId}`)
    },
    updateMyProfile(data) {
        return api.put('/users/me', data)
    },
    updatePassword(currentPassword, newPassword) {
        return api.put('/users/me/password', { currentPassword, newPassword })
    },
    deleteAccount(password) {
        return api.delete('/users/me', { data: { password } }) // DELETE usually sends data in config
    },
    // Settings
    getUserSettings() {
        return api.get('/users/me/settings')
    },
    updateUserSettings(data) {
        return api.put('/users/me/settings', data)
    },
    getNotificationSettings() {
        return api.get('/users/me/notification-settings')
    },
    updateNotificationSettings(data) {
        return api.put('/users/me/notification-settings', data)
    },
    // Blocks
    blockUser(userId) {
        return api.post(`/users/${userId}/block`)
    },
    unblockUser(userId) {
        return api.delete(`/users/${userId}/block`)
    },
    getBlockList() {
        return api.get('/users/me/blocks')
    },
    // Activity
    getMyPosts(params) {
        return api.get('/users/me/posts', { params })
    },
    getMyComments(params) {
        return api.get('/users/me/comments', { params })
    },
    getMyScraps(params) {
        return api.get('/users/me/scraps', { params })
    },
    getMyDrafts(params) {
        return api.get('/users/me/drafts', { params })
    },
    getRecentlyViewedPosts(params) {
        return api.get('/users/me/history/views', { params })
    }
}