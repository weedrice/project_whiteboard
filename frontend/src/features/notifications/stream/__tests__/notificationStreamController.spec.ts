import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
import {
    createNotificationStreamController,
    resetNotificationStreamStateForTest,
} from '@/features/notifications/stream/notificationStreamController'
import type { Notification } from '@/types'
import { subscribeNotificationStreamConnection } from '@/features/notifications/stream/notificationStreamConnectionEvents'

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

    it('publishes the server connection id for session-bound comment topic subscriptions', async () => {
        const connections: Array<{ connectionId: string, sessionGeneration: number } | null> = []
        const stop = subscribeNotificationStreamConnection((connection) => connections.push(connection))
        const openStream = vi.fn(() => Promise.resolve({
            ok: true,
            body: createSseStream('event: connect\ndata: 123e4567-e89b-12d3-a456-426614174000\n\n'),
        } as Response))
        const authStore = {
            accessToken: 'test-token',
            sessionGeneration: 7,
            applyTokenIfCurrent: vi.fn(),
        }
        const queryClient = {
            setQueriesData: vi.fn(),
            setQueryData: vi.fn(),
        } as unknown as QueryClient

        createNotificationStreamController(queryClient, {
            openStream,
            resolveAuthStore: (() => authStore) as never,
        }).connectToSse()
        await flushAsync()

        expect(connections).toContainEqual({
            connectionId: '123e4567-e89b-12d3-a456-426614174000',
            sessionGeneration: 7,
        })
        stop()
    })

    it('ignores a stale connect event that arrives from an aborted stream', async () => {
        const encoder = new TextEncoder()
        let oldStreamController!: ReadableStreamDefaultController<Uint8Array>
        const oldStream = new ReadableStream<Uint8Array>({
            start(controller) {
                oldStreamController = controller
            },
        })
        const newStream = new ReadableStream<Uint8Array>({
            start(controller) {
                controller.enqueue(encoder.encode('event: connect\ndata: connection-new\n\n'))
            },
        })
        const openStream = vi.fn()
            .mockResolvedValueOnce({ ok: true, body: oldStream } as Response)
            .mockResolvedValueOnce({ ok: true, body: newStream } as Response)
        const authStore = {
            accessToken: 'test-token',
            sessionGeneration: 7,
            applyTokenIfCurrent: vi.fn(),
        }
        const queryClient = {
            setQueriesData: vi.fn(),
            setQueryData: vi.fn(),
        } as unknown as QueryClient
        const connections: Array<{ connectionId: string, sessionGeneration: number } | null> = []
        const stop = subscribeNotificationStreamConnection((connection) => connections.push(connection))
        const controller = createNotificationStreamController(queryClient, {
            openStream,
            resolveAuthStore: (() => authStore) as never,
        })

        controller.connectToSse()
        await flushAsync()
        controller.closeSse()
        controller.connectToSse()
        await flushAsync()

        oldStreamController.enqueue(encoder.encode('event: connect\ndata: connection-old\n\n'))
        oldStreamController.enqueue(encoder.encode(
            'event: comment-topic-invalidated\ndata: {"boardId":100}\n\n',
        ))
        oldStreamController.close()
        await flushAsync()

        expect(connections).toContainEqual({ connectionId: 'connection-new', sessionGeneration: 7 })
        expect(connections).not.toContainEqual({ connectionId: 'connection-old', sessionGeneration: 7 })
        expect(openStream).toHaveBeenCalledTimes(2)

        controller.closeSse()
        stop()
    })

    it('reconnects the current session after a comment topic invalidation', async () => {
        const encoder = new TextEncoder()
        let firstController!: ReadableStreamDefaultController<Uint8Array>
        const firstStream = new ReadableStream<Uint8Array>({
            start(controller) {
                firstController = controller
                controller.enqueue(encoder.encode('event: connect\ndata: connection-old\n\n'))
            },
        })
        const secondStream = new ReadableStream<Uint8Array>({
            start(controller) {
                controller.enqueue(encoder.encode('event: connect\ndata: connection-new\n\n'))
            },
        })
        const openStream = vi.fn()
            .mockResolvedValueOnce({ ok: true, body: firstStream } as Response)
            .mockResolvedValueOnce({ ok: true, body: secondStream } as Response)
        const authStore = {
            accessToken: 'test-token',
            sessionGeneration: 7,
            applyTokenIfCurrent: vi.fn(),
        }
        const queryClient = {
            setQueriesData: vi.fn(),
            setQueryData: vi.fn(),
        } as unknown as QueryClient
        const controller = createNotificationStreamController(queryClient, {
            openStream,
            resolveAuthStore: (() => authStore) as never,
        })

        controller.connectToSse()
        await flushAsync()
        firstController.enqueue(encoder.encode(
            'event: comment-topic-invalidated\ndata: {"boardId":100}\n\n',
        ))
        await flushAsync()
        await vi.runOnlyPendingTimersAsync()
        await flushAsync()

        expect(openStream).toHaveBeenCalledTimes(2)
        expect(openStream).toHaveBeenLastCalledWith('test-token', expect.any(AbortSignal))

        controller.closeSse()
    })
})
