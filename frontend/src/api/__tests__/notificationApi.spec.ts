import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    put: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

vi.mock('@/utils/constants', () => ({
    API: {
        BASE_URL: 'https://api.example.com/api/v1/',
        TIMEOUT: 10000,
    },
}))

import { getNotificationStreamUrl, normalizeNotification, notificationApi } from '../notification'

describe('notificationApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls notification endpoints with existing paths and params', () => {
        const params = { page: 1, size: 20 }
        apiMock.get.mockResolvedValueOnce({
            data: {
                data: {
                    content: [],
                    number: 1,
                    size: 20,
                    totalElements: 0,
                    totalPages: 0,
                    empty: true,
                },
            },
        })

        notificationApi.getNotifications(params)
        notificationApi.markAsRead(10)
        notificationApi.markAllAsRead()
        notificationApi.getUnreadCount()

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/notifications', { params })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/notifications/10/read')
        expect(apiMock.put).toHaveBeenNthCalledWith(2, '/notifications/read-all')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/notifications/unread-count')
    })

    it('builds the SSE stream URL from the configured API base URL', () => {
        expect(getNotificationStreamUrl()).toBe('https://api.example.com/api/v1/notifications/stream')
    })

    it('normalizes snake_case notification payloads', () => {
        expect(normalizeNotification({
            notification_id: 12,
            source_type: 'COMMENT',
            source_id: 34,
            is_read: 'Y',
            created_at: '2026-05-19T01:00:00Z',
            message: 'hello',
            actor: {
                user_id: 5,
                author_type: 'USER',
                display_name: 'Alice',
                profile_image_url: '/profile.png',
            },
        })).toEqual({
            notificationId: 12,
            sourceType: 'COMMENT',
            sourceId: 34,
            isRead: true,
            createdAt: '2026-05-19T01:00:00Z',
            message: 'hello',
            actor: {
                userId: 5,
                agentId: undefined,
                authorType: 'USER',
                displayName: 'Alice',
                profileImageUrl: '/profile.png',
            },
            actorDisplayName: 'Alice',
            actorInitial: 'A',
            targetUrl: undefined,
        })
    })

    it('normalizes blank system actors to a safe display model', () => {
        expect(normalizeNotification({
            notification_id: 13,
            source_type: 'SYSTEM',
            actor: {
                author_type: 'SYSTEM',
                display_name: '',
            },
        })).toMatchObject({
            notificationId: 13,
            sourceType: 'SYSTEM',
            actor: {
                authorType: 'SYSTEM',
                displayName: '',
            },
            actorDisplayName: 'System',
            actorInitial: 'S',
        })
    })

    it('uses the system fallback when a system notification has no actor payload', () => {
        expect(normalizeNotification({
            notification_id: 14,
            source_type: 'SYSTEM',
        })).toMatchObject({
            notificationId: 14,
            sourceType: 'SYSTEM',
            actorDisplayName: 'System',
            actorInitial: 'S',
        })
    })
})
