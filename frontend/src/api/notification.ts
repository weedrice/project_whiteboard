import api from './index'
import type { ApiResponse, Notification, PageResponse } from '@/types'
import type { AxiosResponse } from 'axios'
import { API } from '@/utils/constants'

export interface NotificationParams {
    page?: number;
    size?: number;
}

// API 응답 원본 타입 (snake_case)
interface NotificationActorRaw {
    userId?: number;
    user_id?: number;
    agentId?: number;
    agent_id?: number;
    authorType?: 'USER' | 'AGENT' | 'SYSTEM';
    author_type?: 'USER' | 'AGENT' | 'SYSTEM';
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

interface NotificationPageRaw extends Partial<PageResponse<NotificationRaw>> {
    page?: number;
    hasNext?: boolean;
    hasPrevious?: boolean;
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
            agentId: raw.actor?.agentId || raw.actor?.agent_id,
            authorType: raw.actor?.authorType || raw.actor?.author_type,
            displayName: raw.actor?.displayName || raw.actor?.display_name || '',
            profileImageUrl: raw.actor?.profileImageUrl || raw.actor?.profile_image_url
        },
        targetUrl: raw.targetUrl
    }
}

function normalizeNotificationPage(raw: NotificationPageRaw): PageResponse<Notification> {
    const content = (raw.content || []).map(normalizeNotification)
    const number = typeof raw.number === 'number'
        ? raw.number
        : (typeof raw.page === 'number' ? raw.page : 0)
    const size = typeof raw.size === 'number' ? raw.size : content.length
    const totalElements = typeof raw.totalElements === 'number' ? raw.totalElements : content.length
    const totalPages = typeof raw.totalPages === 'number'
        ? raw.totalPages
        : (size > 0 ? Math.max(1, Math.ceil(totalElements / size)) : (content.length > 0 ? 1 : 0))
    const first = typeof raw.first === 'boolean' ? raw.first : number <= 0
    const last = typeof raw.last === 'boolean'
        ? raw.last
        : (typeof raw.hasNext === 'boolean' ? !raw.hasNext : totalPages <= 1 || number >= totalPages - 1)
    const empty = typeof raw.empty === 'boolean' ? raw.empty : content.length === 0

    return {
        content,
        totalElements,
        totalPages,
        size,
        number,
        first,
        last,
        empty,
    }
}

function buildNotificationStreamUrl(): string {
    return `${API.BASE_URL.replace(/\/+$/, '')}/notifications/stream`
}

export const notificationApi = {
    // Get notifications
    getNotifications: async (params: NotificationParams): Promise<AxiosResponse<ApiResponse<PageResponse<Notification>>>> => {
        const response = await api.get<ApiResponse<NotificationPageRaw>>('/notifications', { params })
        const normalizedPage = normalizeNotificationPage(response.data.data || {})
        const normalizedResponse: AxiosResponse<ApiResponse<PageResponse<Notification>>> = {
            ...response,
            data: {
                ...response.data,
                data: normalizedPage
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

    openStream: (token: string, signal: AbortSignal): Promise<Response> => {
        return fetch(buildNotificationStreamUrl(), {
            method: 'GET',
            headers: {
                Accept: 'text/event-stream',
                Authorization: `Bearer ${token}`,
            },
            cache: 'no-store',
            credentials: 'same-origin',
            signal,
        })
    },
}
