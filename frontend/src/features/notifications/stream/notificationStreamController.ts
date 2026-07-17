import type { QueryClient } from '@tanstack/vue-query'
import { authApi } from '@/api/auth'
import { unwrapAxiosApiData } from '@/api/response'
import {
    notificationApi,
    normalizeNotification as normalizeNotificationResponse,
    type CommentStreamEvent,
    type NotificationRaw,
} from '@/api/notification'
import logger from '@/utils/logger'
import { useAuthStore } from '@/stores/auth'
import { isCancellationError } from '@/utils/cancellationError'
import { emitCommentStreamEvent } from '@/features/comments/commentStreamEvents'
import { consumeSseStream } from '@/features/notifications/stream/notificationSseStream'
import {
    shouldStopNotificationReconnectAfterRefresh,
} from '@/features/notifications/stream/notificationStreamStateModel'
import {
    applyIncomingNotificationToCache,
    getRawNotificationId,
} from '@/features/notifications/queries/notificationCacheUpdater'
import { NotificationStreamRuntime } from '@/features/notifications/stream/notificationReconnectRuntime'
import { emitBadgeAwardEvent } from '@/features/notifications/events/badgeAwardEvents'
import { coordinateAuthRefresh } from '@/api/authRefreshCoordinator'

function isAbortError(error: unknown): boolean {
    return isCancellationError(error, {
        names: ['AbortError'],
        requireDomException: true,
    })
}

const RECONNECT_AFTER_REFRESH_DELAY_MS = 1000
const RECONNECT_AFTER_FAILURE_DELAY_MS = 5000

export interface NotificationStreamControllerDependencies {
    openStream?: typeof notificationApi.openStream
    refreshToken?: typeof authApi.refreshToken
    normalizeNotification?: typeof normalizeNotificationResponse
    resolveAuthStore?: typeof useAuthStore
    isBrowserOnline?: () => boolean
}

function getErrorStatus(error: unknown): number | null {
    if (!error || typeof error !== 'object') return null
    const response = (error as { response?: { status?: unknown } }).response
    return typeof response?.status === 'number' ? response.status : null
}

const notificationStreamRuntime = new NotificationStreamRuntime()

export function resetNotificationStreamStateForTest() {
    notificationStreamRuntime.reset()
}

export function resetNotificationStreamSessionState() {
    notificationStreamRuntime.resetSessionState()
}

function scheduleReconnect(delayMs: number) {
    notificationStreamRuntime.scheduleReconnect(delayMs)
}

export function createNotificationStreamController(
    queryClient: QueryClient,
    dependencies: NotificationStreamControllerDependencies = {},
) {
    const openStream = dependencies.openStream ?? notificationApi.openStream
    const refreshToken = dependencies.refreshToken ?? authApi.refreshToken
    const normalizeIncomingNotification = dependencies.normalizeNotification ?? normalizeNotificationResponse
    const resolveAuthStore = dependencies.resolveAuthStore ?? useAuthStore
    notificationStreamRuntime.setOnlineProvider(dependencies.isBrowserOnline)

    const handleSseEvent = (eventType: string, payload: string, sessionGeneration: number) => {
        if (!payload) return
        if (eventType === 'comment') {
            handleCommentSseEvent(payload, sessionGeneration)
            return
        }
        if (eventType !== 'notification' && eventType !== 'message') return

        try {
            const rawNotification = JSON.parse(payload) as NotificationRaw
            if (getRawNotificationId(rawNotification) == null) return
            if (resolveAuthStore().sessionGeneration !== sessionGeneration) return
            const notification = normalizeIncomingNotification(rawNotification)
            const isDuplicate = notificationStreamRuntime.state.recentNotificationIds.has(notification.notificationId)
            applyIncomingNotificationToCache(
                queryClient,
                notification,
                notificationStreamRuntime.state.recentNotificationIds,
                sessionGeneration,
            )
            if (!isDuplicate) emitBadgeAwardEvent(notification)
        } catch (error: unknown) {
            logger.error('Failed to parse SSE notification:', error)
        }
    }

    const handleCommentSseEvent = (payload: string, sessionGeneration: number) => {
        try {
            const event = JSON.parse(payload) as Partial<CommentStreamEvent>
            if (resolveAuthStore().sessionGeneration !== sessionGeneration) return
            if (typeof event.postId !== 'number' || typeof event.commentId !== 'number') return
            if (event.action !== 'CREATED' && event.action !== 'DELETED') return
            if (typeof event.actorUserId !== 'number' || typeof event.occurredAt !== 'string') return
            emitCommentStreamEvent({
                ...event,
                sessionGeneration,
            } as CommentStreamEvent)
        } catch (error: unknown) {
            logger.error('Failed to parse SSE comment event:', error)
        }
    }

    const reconnectWithRefresh = async (controller: AbortController) => {
        if (!notificationStreamRuntime.isBrowserOnline()) {
            scheduleReconnect(RECONNECT_AFTER_FAILURE_DELAY_MS)
            return
        }

        try {
            const authStore = resolveAuthStore()
            const generation = authStore.sessionGeneration
            const previousToken = authStore.accessToken
            const refreshedAccessToken = await coordinateAuthRefresh(async () => {
                if (controller.signal.aborted
                    || authStore.sessionGeneration !== generation
                    || authStore.accessToken !== previousToken) {
                    throw new DOMException('Authentication session changed', 'AbortError')
                }
                return unwrapAxiosApiData(await refreshToken({
                    skipAuthRefresh: true,
                    skipGlobalErrorHandler: true,
                    signal: controller.signal,
                })).accessToken
            })
            const applied = authStore.applyTokenIfCurrent(generation, previousToken, refreshedAccessToken)
            if (!applied && (authStore.sessionGeneration !== generation || authStore.accessToken !== refreshedAccessToken)) return
            scheduleReconnect(RECONNECT_AFTER_REFRESH_DELAY_MS)
            return
        } catch (error: unknown) {
            logger.warn('SSE reconnect: refresh failed', error)
            const status = getErrorStatus(error)
            if (shouldStopNotificationReconnectAfterRefresh(status)) {
                notificationStreamRuntime.state.closedManually = true
                return
            }
        }

        scheduleReconnect(RECONNECT_AFTER_FAILURE_DELAY_MS)
    }

    const startStream = async (
        token: string,
        sessionGeneration: number,
        controller: AbortController,
    ) => {
        try {
            const response = await openStream(token, controller.signal)

            if (!response.ok) {
                throw new Error(`SSE stream request failed: ${response.status}`)
            }
            if (!response.body) {
                throw new Error('SSE stream response is empty')
            }

            notificationStreamRuntime.state.isConnecting = false
            notificationStreamRuntime.state.reconnectAttempt = 0
            await consumeSseStream(
                response.body,
                controller.signal,
                (eventType, payload) => handleSseEvent(eventType, payload, sessionGeneration),
            )

            if (!controller.signal.aborted && !notificationStreamRuntime.state.closedManually) {
                throw new Error('SSE stream closed unexpectedly')
            }
        } catch (error: unknown) {
            if (notificationStreamRuntime.state.closedManually || controller.signal.aborted || isAbortError(error)) {
                return
            }

            logger.warn('SSE connection dropped:', error)
            await reconnectWithRefresh(controller)
        } finally {
            if (notificationStreamRuntime.state.streamAbortController === controller) {
                notificationStreamRuntime.state.streamAbortController = null
            }
            notificationStreamRuntime.state.isConnecting = false
        }
    }

    const connectToSse = () => {
        const { state } = notificationStreamRuntime
        if (state.streamAbortController || state.isConnecting) return

        if (state.reconnectTimer) {
            notificationStreamRuntime.clearReconnectTimer()
        }
        notificationStreamRuntime.detachOnlineReconnectListener()
        state.reconnectWhenOnline = false

        const authStore = resolveAuthStore()
        const token = authStore.accessToken
        if (!token) return

        state.closedManually = false
        state.isConnecting = true

        const controller = new AbortController()
        state.streamAbortController = controller

        void startStream(token, authStore.sessionGeneration, controller)
    }

    notificationStreamRuntime.setReconnectCallback(connectToSse)

    const closeSse = () => {
        const { state } = notificationStreamRuntime
        state.closedManually = true

        notificationStreamRuntime.clearReconnectTimer()
        notificationStreamRuntime.abortStream()

        state.isConnecting = false
        state.reconnectAttempt = 0
        state.reconnectWhenOnline = false
        state.recentNotificationIds.clear()
        notificationStreamRuntime.detachOnlineReconnectListener()
    }

    return {
        connectToSse,
        closeSse,
    }
}
