import api from './index'
import type { ApiResponse, Notification } from '@/types'

export interface NotificationParams {
    page?: number;
    size?: number;
}

export const notificationApi = {
    // Get notifications
    getNotifications: (params: NotificationParams) => api.get<ApiResponse<Notification[]>>('/notifications', { params }),

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put<ApiResponse<void>>(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get<ApiResponse<number>>('/notifications/unread-count'),
}
