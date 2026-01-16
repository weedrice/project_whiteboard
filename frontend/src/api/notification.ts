import api from './index'
import type { ApiResponse, Notification, NotificationActor, PageResponse } from '@/types'
import type { AxiosResponse } from 'axios'

export interface NotificationParams {
    page?: number;
    size?: number;
}

// API 응답 원본 타입 (snake_case)
interface NotificationActorRaw {
    userId?: number;
    user_id?: number;
    displayName?: string;
    display_name?: string;
    profileImageUrl?: string;
    profile_image_url?: string;
}

interface NotificationRaw {
    notificationId?: number;
    notification_id?: number;
    message?: string;
    sourceType?: 'POST' | 'COMMENT' | 'SYSTEM';
    source_type?: 'POST' | 'COMMENT' | 'SYSTEM';
    sourceId?: number;
    source_id?: number;
    isRead?: boolean;
    is_read?: boolean | string;
    createdAt?: string;
    created_at?: string;
    actor?: NotificationActorRaw;
    targetUrl?: string;
}

export const notificationApi = {
    // Get notifications
    getNotifications: async (params: NotificationParams) => {
        const response = await api.get<ApiResponse<PageResponse<NotificationRaw>>>('/notifications', { params })
        if (response.data.success) {
            response.data.data.content = response.data.data.content.map((n: NotificationRaw) => ({
                ...n,
                notificationId: n.notificationId || n.notification_id || 0,
                sourceType: n.sourceType || n.source_type || 'SYSTEM',
                sourceId: n.sourceId || n.source_id || 0,
                isRead: n.isRead === true || n.is_read === true || n.is_read === 'Y',
                createdAt: n.createdAt || n.created_at || '',
                message: n.message || '',
                actor: {
                    ...n.actor,
                    userId: n.actor?.userId || n.actor?.user_id || 0,
                    displayName: n.actor?.displayName || n.actor?.display_name || '',
                    profileImageUrl: n.actor?.profileImageUrl || n.actor?.profile_image_url
                }
            })) as Notification[]
        }
        return response as unknown as AxiosResponse<ApiResponse<PageResponse<Notification>>>
    },

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put<ApiResponse<void>>(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get<ApiResponse<number>>('/notifications/unread-count'),
}
