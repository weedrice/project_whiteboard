import api from './index'

export const userApi = {
    // Get current user profile
    getMyProfile: () => api.get('/users/me'),

    // Get posts created by current user
    getMyPosts: (params) => api.get('/users/me/posts', { params }),

    // Get blocked users
    getBlockedUsers: () => api.get('/users/me/blocks'),

    // Block a user
    blockUser: (userId) => api.post(`/users/${userId}/block`),

    // Unblock a user
    unblockUser: (userId) => api.delete(`/users/${userId}/block`),

    // Get user settings
    getUserSettings: () => api.get('/users/me/settings'),

    // Update user settings
    updateUserSettings: (settingsData) => api.put('/users/me/settings', settingsData),

    // Get notification settings
    getNotificationSettings: () => api.get('/users/me/notification-settings'),

    // Update notification settings
    updateNotificationSettings: (settingsData) => api.put('/users/me/notification-settings', settingsData),

    // Get my scraps
    getMyScraps: (params) => api.get('/users/me/scraps', { params }),

    // Update my profile
    updateMyProfile: (profileData) => api.put('/users/me', profileData),
}
