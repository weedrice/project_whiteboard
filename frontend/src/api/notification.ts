import api from './index'
import type { ApiResponse, Notification, PageResponse } from '@/types'
import type { AxiosResponse } from 'axios'

export interface NotificationParams {
    page?: number;
    size?: number;
}

export const notificationApi = {
    // Get notifications
    // Get notifications
    getNotifications: async (params: NotificationParams) => {
        const response = await api.get<ApiResponse<PageResponse<any>>>('/notifications', { params })
        if (response.data.success) {
            response.data.data.content = response.data.data.content.map((n: any) => ({
                ...n,
                notificationId: n.notificationId || n.notification_id,
                sourceType: n.sourceType || n.source_type,
                sourceId: n.sourceId || n.source_id,
                isRead: n.isRead === true || n.is_read === true || n.is_read === 'Y',
                createdAt: n.createdAt || n.created_at,
                actor: {
                    ...n.actor,
                    userId: n.actor?.userId || n.actor?.user_id,
                    displayName: n.actor?.displayName || n.actor?.display_name,
                    profileImageUrl: n.actor?.profileImageUrl || n.actor?.profile_image_url
                }
            }))
        }
        return response as AxiosResponse<ApiResponse<PageResponse<Notification>>>
    },

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put<ApiResponse<void>>(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get<ApiResponse<number>>('/notifications/unread-count'),
}
