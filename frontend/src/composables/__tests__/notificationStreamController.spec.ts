import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
import {
    createNotificationStreamController,
    resetNotificationStreamStateForTest,
} from '@/composables/notificationStreamController'
import type { Notification } from '@/types'

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
        warn: vi.fn(),
    },
}))

function createSseStream(payload: string): ReadableStream<Uint8Array> {
    const encoder = new TextEncoder()
    return new ReadableStream<Uint8Array>({
        start(controller) {
            controller.enqueue(encoder.encode(payload))
            controller.close()
        },
    })
}

async function flushAsync(cycles = 5) {
    for (let i = 0; i < cycles; i += 1) {
        await Promise.resolve()
    }
}

describe('notificationStreamController dependencies', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        resetNotificationStreamStateForTest()
    })

    afterEach(() => {
        resetNotificationStreamStateForTest()
        vi.useRealTimers()
        vi.restoreAllMocks()
    })

    it('uses injected stream, normalizer, and auth store dependencies', async () => {
        let firstPage: Record<string, unknown> = {
            content: [],
            number: 0,
            size: 20,
            totalElements: 0,
            empty: true,
        }
        let unreadCount = 0
        const queryClient = {
            setQueriesData: vi.fn((_filter, updater: (oldData: unknown) => unknown) => {
                firstPage = updater(firstPage) as Record<string, unknown>
                return firstPage
            }),
            setQueryData: vi.fn((_key, updater: (old: number | undefined) => number) => {
                unreadCount = updater(unreadCount)
                return unreadCount
            }),
        } as unknown as QueryClient
        const notification: Notification = {
            notificationId: 12,
            notificationType: 'COMMENT',
            sourceType: 'POST',
            sourceId: 3,
            isRead: true,
            createdAt: '2026-01-01T00:00:00Z',
            message: 'hello',
            actor: {
                userId: 1,
                displayName: 'User',
            },
            actorDisplayName: 'User',
            actorInitial: 'U',
        }
        const normalizeNotification = vi.fn(() => notification)
        const openStream = vi.fn((_token: string, _signal: AbortSignal) => Promise.resolve({
            ok: true,
            body: createSseStream('event: notification\ndata: {"notification_id":12}\n\n'),
        } as Response))
        const refreshToken = vi.fn(() => Promise.resolve({
            data: {
                data: {
                    accessToken: 'new-token',
                    expiresIn: 3600,
                },
            },
        }))
        const authStore = {
            accessToken: 'test-token',
            setTokens: vi.fn(),
        }
        const resolveAuthStore = vi.fn(() => authStore)

        const { connectToSse } = createNotificationStreamController(queryClient, {
            openStream,
            refreshToken: refreshToken as never,
            normalizeNotification,
            resolveAuthStore: resolveAuthStore as never,
        })
        connectToSse()
        await flushAsync()

        expect(resolveAuthStore).toHaveBeenCalled()
        expect(openStream).toHaveBeenCalledWith('test-token', expect.any(AbortSignal))
        expect(normalizeNotification).toHaveBeenCalledWith({ notification_id: 12 })
        expect(firstPage.content).toEqual([{ ...notification, isRead: false }])
        expect(unreadCount).toBe(1)
    })
})
