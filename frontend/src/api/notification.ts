import api from './index'

export interface NotificationActor {
    userId: number;
    displayName: string;
    profileImageUrl?: string;
}

export interface Notification {
    notificationId: number;
    message: string;
    sourceType: 'POST' | 'COMMENT' | 'SYSTEM';
    sourceId: number;
    isRead: boolean;
    createdAt: string;
    actor: NotificationActor;
}

export interface NotificationParams {
    page?: number;
    size?: number;
}

export const notificationApi = {
    // Get notifications
    getNotifications: (params: NotificationParams) => api.get('/notifications', { params }),

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get('/notifications/unread-count'),
}
