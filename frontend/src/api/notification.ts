import api from './index'
import { mapApiPageResponse } from '@/api/response'
import type { ApiResponse, Notification, PageResponse } from '@/types'
import type { AxiosResponse } from 'axios'
import { API } from '@/utils/constants'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'

export interface NotificationParams {
    page?: number;
    size?: number;
}

// API 응답 원본 타입 (snake_case)
export interface NotificationActorRaw {
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

export interface NotificationRaw {
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
    target_url?: string;
}

interface NotificationPageRaw extends PageResponseRaw<NotificationRaw> {
    page?: number;
    hasNext?: boolean;
    hasPrevious?: boolean;
}

const SYSTEM_ACTOR_DISPLAY_NAME = 'System'
const UNKNOWN_ACTOR_DISPLAY_NAME = 'Unknown'
const UNKNOWN_ACTOR_INITIAL = '?'

function getActorDisplayName(raw: NotificationRaw): string {
    const actor = raw.actor
    const displayName = (actor?.displayName || actor?.display_name || '').trim()
    if (displayName) return displayName
    const authorType = actor?.authorType || actor?.author_type
    const sourceType = raw.sourceType || raw.source_type
    return authorType === 'SYSTEM' || sourceType === 'SYSTEM' ? SYSTEM_ACTOR_DISPLAY_NAME : UNKNOWN_ACTOR_DISPLAY_NAME
}

function getActorInitial(displayName: string): string {
    return Array.from(displayName.trim())[0]?.toUpperCase() || UNKNOWN_ACTOR_INITIAL
}

export function normalizeNotification(raw: NotificationRaw): Notification {
    const actorDisplayName = getActorDisplayName(raw)

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
        actorDisplayName,
        actorInitial: getActorInitial(actorDisplayName),
        targetUrl: raw.targetUrl || raw.target_url
    }
}

function normalizeNotificationPage(raw: NotificationPageRaw): PageResponse<Notification> {
    return normalizePageResponse({
        ...raw,
        content: (raw.content || []).map(normalizeNotification),
    }, {
        fallbackTotalPages: ({ size, totalElements, contentLength }) =>
            size > 0 ? Math.max(1, Math.ceil(totalElements / size)) : (contentLength > 0 ? 1 : 0),
    })
}

export function getNotificationStreamUrl(): string {
    return `${API.BASE_URL.replace(/\/+$/, '')}/notifications/stream`
}

export const notificationApi = {
    // Get notifications
    getNotifications: async (params: NotificationParams): Promise<AxiosResponse<ApiResponse<PageResponse<Notification>>>> => {
        const response = await api.get<ApiResponse<NotificationPageRaw>>('/notifications', { params })
        return mapApiPageResponse(response, (raw) => normalizeNotificationPage(raw || {}), { mapNullish: true })
    },

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put<ApiResponse<void>>(`/notifications/${notificationId}/read`),

    // Mark all as read
    markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/read-all'),

    // Get unread count
    getUnreadCount: () => api.get<ApiResponse<number>>('/notifications/unread-count'),

    openStream: (token: string, signal: AbortSignal): Promise<Response> => {
        return fetch(getNotificationStreamUrl(), {
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
