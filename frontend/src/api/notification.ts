import api from './index'
import { mapApiPageResponse } from '@/api/response'
import type { ApiResponse, Notification, PageResponse } from '@/types'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { API } from '@/utils/constants'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'

export interface NotificationParams {
    page?: number;
    size?: number;
}

// API response source shape, including snake_case fields.
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
    notificationType?: Notification['notificationType'];
    notification_type?: Notification['notificationType'];
    sourceType?: Notification['sourceType'];
    source_type?: Notification['sourceType'];
    sourceId?: number;
    source_id?: number;
    isRead?: boolean;
    is_read?: boolean | string;
    createdAt?: string;
    created_at?: string;
    groupCount?: number;
    group_count?: number;
    grouped?: boolean;
    lastEventAt?: string;
    last_event_at?: string;
    actor?: NotificationActorRaw;
    targetUrl?: string;
    target_url?: string;
}

interface NotificationPageRaw extends PageResponseRaw<NotificationRaw> {
    page?: number;
    hasNext?: boolean;
    hasPrevious?: boolean;
}

export const NOTIFICATION_SYSTEM_ACTOR_DISPLAY_NAME = 'System'
export const NOTIFICATION_UNKNOWN_ACTOR_DISPLAY_NAME = 'Unknown'
export const NOTIFICATION_UNKNOWN_ACTOR_INITIAL = '?'

function getActorDisplayName(raw: NotificationRaw): string {
    const actor = raw.actor
    const displayName = (actor?.displayName || actor?.display_name || '').trim()
    if (displayName) return displayName
    const authorType = actor?.authorType || actor?.author_type
    const sourceType = raw.sourceType || raw.source_type
    return authorType === 'SYSTEM' || sourceType === 'SYSTEM'
        ? NOTIFICATION_SYSTEM_ACTOR_DISPLAY_NAME
        : NOTIFICATION_UNKNOWN_ACTOR_DISPLAY_NAME
}

function getActorInitial(displayName: string): string {
    return Array.from(displayName.trim())[0]?.toUpperCase() || NOTIFICATION_UNKNOWN_ACTOR_INITIAL
}

export function normalizeNotification(raw: NotificationRaw): Notification {
    const actorDisplayName = getActorDisplayName(raw)

    return {
        notificationId: raw.notificationId || raw.notification_id || 0,
        notificationType: raw.notificationType || raw.notification_type || 'SYSTEM',
        sourceType: raw.sourceType || raw.source_type || 'SYSTEM',
        sourceId: raw.sourceId || raw.source_id || 0,
        isRead: raw.isRead === true || raw.is_read === true || raw.is_read === 'Y',
        createdAt: raw.createdAt || raw.created_at || '',
        groupCount: raw.groupCount ?? raw.group_count ?? 1,
        grouped: raw.grouped ?? ((raw.groupCount ?? raw.group_count ?? 1) > 1),
        lastEventAt: raw.lastEventAt || raw.last_event_at,
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
    getNotifications: async (
        params: NotificationParams,
        config?: AxiosRequestConfig
    ): Promise<AxiosResponse<ApiResponse<PageResponse<Notification>>>> => {
        const response = await api.get<ApiResponse<NotificationPageRaw>>('/notifications', { ...config, params })
        return mapApiPageResponse(response, (raw) => normalizeNotificationPage(raw || {}), { mapNullish: true })
    },

    // Mark as read
    markAsRead: (notificationId: string | number) => api.put<ApiResponse<void>>(`/notifications/${encodePathSegment(notificationId)}/read`),

    // Mark all as read
    markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/read-all'),

    // Get unread count
    getUnreadCount: (config?: AxiosRequestConfig) => config
        ? api.get<ApiResponse<number>>('/notifications/unread-count', config)
        : api.get<ApiResponse<number>>('/notifications/unread-count'),

    // Use fetch for SSE because Axios does not expose a browser ReadableStream body.
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
