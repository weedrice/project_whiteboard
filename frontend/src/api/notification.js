import api from './index'

export const notificationApi = {
    // Get notifications
    getNotifications: (params) => api.get('/notifications', { params }),

    // Mark as read
    markAsRead: (notificationId) => api.put(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get('/notifications/unread-count'),
}
