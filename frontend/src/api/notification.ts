import api from './index'
import type { ApiResponse, Notification, PageResponse } from '@/types'
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

function normalizeNotification(raw: NotificationRaw): Notification {
    return {
        notificationId: raw.notificationId || raw.notification_id || 0,
        sourceType: raw.sourceType || raw.source_type || 'SYSTEM',
        sourceId: raw.sourceId || raw.source_id || 0,
        isRead: raw.isRead === true || raw.is_read === true || raw.is_read === 'Y',
        createdAt: raw.createdAt || raw.created_at || '',
        message: raw.message || '',
        actor: {
            userId: raw.actor?.userId || raw.actor?.user_id || 0,
            displayName: raw.actor?.displayName || raw.actor?.display_name || '',
            profileImageUrl: raw.actor?.profileImageUrl || raw.actor?.profile_image_url
        },
        targetUrl: raw.targetUrl
    }
}

export const notificationApi = {
    // Get notifications
    getNotifications: async (params: NotificationParams): Promise<AxiosResponse<ApiResponse<PageResponse<Notification>>>> => {
        const response = await api.get<ApiResponse<PageResponse<NotificationRaw>>>('/notifications', { params })
        const normalizedResponse: AxiosResponse<ApiResponse<PageResponse<Notification>>> = {
            ...response,
            data: {
                ...response.data,
                data: {
                    ...response.data.data,
                    content: (response.data.data.content || []).map(normalizeNotification)
                }
            }
        }
        return normalizedResponse
    },

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put<ApiResponse<void>>(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get<ApiResponse<number>>('/notifications/unread-count'),
}
