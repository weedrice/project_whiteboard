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
import { consumeSseStream, SseProtocolLimitError } from '@/features/notifications/stream/notificationSseStream'
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
import { handleTerminalAuthFailure } from '@/api/authTerminalFailure'
import { setNotificationStreamConnection } from '@/features/notifications/stream/notificationStreamConnectionEvents'
import { emitMessageStreamEvent } from '@/features/user/messages/messageStreamEvents'
import { invalidateScheduledPostNotificationCaches } from '@/features/board/posts/queries/scheduledPostNotificationCacheInvalidation'
import { NOTIFICATION_SSE_EVENTS, SSE_DEFAULT_EVENT_NAME } from '@/features/notifications/stream/notificationSseEvents'
import { shopQueryKeys } from '@/features/shop/shopQueryKeys'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

interface ShopItemSaleStatusChangedEvent {
    itemId: number
    itemType: string
    targetId: number | null
    saleEnabled: boolean
}

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
    setNotificationStreamConnection(null)
}

export function resetNotificationStreamSessionState() {
    notificationStreamRuntime.resetSessionState()
    setNotificationStreamConnection(null)
}

export function recycleNotificationStreamConnection() {
    const { state } = notificationStreamRuntime
    if (state.closedManually || !state.streamAbortController) {
        setNotificationStreamConnection(null)
        return
    }
    notificationStreamRuntime.clearReconnectTimer()
    notificationStreamRuntime.abortStream()
    setNotificationStreamConnection(null)
    state.isConnecting = false
    scheduleReconnect(0)
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

    const handleSseEvent = (
        eventType: string,
        payload: string,
        sessionGeneration: number,
        controller: AbortController,
    ) => {
        if (controller.signal.aborted
            || notificationStreamRuntime.state.streamAbortController !== controller) return
        if (!payload) return
        if (eventType === NOTIFICATION_SSE_EVENTS.CONNECT) {
            if (resolveAuthStore().sessionGeneration !== sessionGeneration) return
            const connectionId = payload.trim()
            if (!connectionId || connectionId.length > 128) return
            setNotificationStreamConnection({ connectionId, sessionGeneration })
            return
        }
        if (eventType === NOTIFICATION_SSE_EVENTS.COMMENT) {
            handleCommentSseEvent(payload, sessionGeneration)
            return
        }
        if (eventType === NOTIFICATION_SSE_EVENTS.COMMENT_TOPIC_INVALIDATED
            || eventType === NOTIFICATION_SSE_EVENTS.COMMENT_TOPIC_ACCESS_REVOKED) {
            if (resolveAuthStore().sessionGeneration === sessionGeneration) {
                if (eventType === NOTIFICATION_SSE_EVENTS.COMMENT_TOPIC_ACCESS_REVOKED) {
                    void queryClient.invalidateQueries({
                        queryKey: ['session', sessionGeneration],
                    })
                }
                recycleNotificationStreamConnection()
            }
            return
        }
        if (eventType === NOTIFICATION_SSE_EVENTS.SHOP_ITEM_SALE_STATUS_CHANGED) {
            handleShopItemSaleStatusChanged(payload, sessionGeneration)
            return
        }
        // SSE 규격상 event: 줄이 없는 프레임은 'message'로 도착하므로 함께 받는다.
        if (eventType !== NOTIFICATION_SSE_EVENTS.NOTIFICATION && eventType !== SSE_DEFAULT_EVENT_NAME) return

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
            if (!isDuplicate) {
                emitBadgeAwardEvent(notification)
                emitMessageStreamEvent(notification)
                invalidateScheduledPostNotificationCaches(queryClient, notification, sessionGeneration)
            }
        } catch (error: unknown) {
            logger.error('Failed to parse SSE notification:', error)
        }
    }

    const handleShopItemSaleStatusChanged = (payload: string, sessionGeneration: number) => {
        try {
            const event = JSON.parse(payload) as Partial<ShopItemSaleStatusChangedEvent>
            if (resolveAuthStore().sessionGeneration !== sessionGeneration) return
            if (typeof event.itemId !== 'number' || typeof event.itemType !== 'string') return
            if (event.targetId !== null && typeof event.targetId !== 'number') return
            if (typeof event.saleEnabled !== 'boolean') return

            void queryClient.invalidateQueries({ queryKey: shopQueryKeys.itemsRoot })
            void queryClient.invalidateQueries({
                queryKey: sessionQueryKey(sessionGeneration, adminQueryKeys.shopItemsRoot),
            })

            if (event.itemType === 'EMOTICON' && event.targetId !== null) {
                void queryClient.invalidateQueries({
                    queryKey: emoticonQueryKeys.detail(event.targetId),
                })
                void queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(
                        sessionGeneration,
                        emoticonQueryKeys.purchaseStatus(event.targetId),
                    ),
                })
            }
        } catch (error: unknown) {
            logger.error('Failed to parse SSE shop sale status event:', error)
        }
    }

    const handleCommentSseEvent = (payload: string, sessionGeneration: number) => {
        try {
            const event = JSON.parse(payload) as Partial<CommentStreamEvent>
            if (resolveAuthStore().sessionGeneration !== sessionGeneration) return
            if (typeof event.postId !== 'number' || typeof event.commentId !== 'number') return
            if (event.action !== 'CREATED' && event.action !== 'UPDATED' && event.action !== 'DELETED') return
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

        const authStore = resolveAuthStore()
        const generation = authStore.sessionGeneration
        const previousToken = authStore.accessToken
        const expectedUserId = authStore.user?.userId ?? null
        try {
            const refreshedAccessToken = await coordinateAuthRefresh(async (signal) => {
                if (controller.signal.aborted
                    || authStore.sessionGeneration !== generation
                    || authStore.accessToken !== previousToken) {
                    throw new DOMException('Authentication session changed', 'AbortError')
                }
                return unwrapAxiosApiData(await refreshToken({
                    skipAuthRefresh: true,
                    skipGlobalErrorHandler: true,
                    signal,
                })).accessToken
            }, { previousToken, signal: controller.signal })
            if (controller.signal.aborted
                || authStore.sessionGeneration !== generation
                || authStore.accessToken !== previousToken) return
            const applied = authStore.applyTokenIfCurrent(generation, previousToken, refreshedAccessToken)
            if (!applied && (authStore.sessionGeneration !== generation || authStore.accessToken !== refreshedAccessToken)) return
            if (expectedUserId != null) {
                const didHydrateSameIdentity = await authStore.fetchUser(
                    { skipAuthRefresh: true, signal: controller.signal },
                    expectedUserId,
                )
                if (!didHydrateSameIdentity
                    || controller.signal.aborted
                    || authStore.sessionGeneration !== generation) {
                    scheduleReconnect(RECONNECT_AFTER_FAILURE_DELAY_MS)
                    return
                }
            }
            scheduleReconnect(RECONNECT_AFTER_REFRESH_DELAY_MS)
            return
        } catch (error: unknown) {
            logger.warn('SSE reconnect: refresh failed', error)
            const status = getErrorStatus(error)
            if (shouldStopNotificationReconnectAfterRefresh(status)) {
                await handleTerminalAuthFailure(status, authStore, {
                    generation,
                    accessToken: previousToken,
                })
                notificationStreamRuntime.state.closedManually = true
                notificationStreamRuntime.resetSessionState()
                setNotificationStreamConnection(null)
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
        setNotificationStreamConnection(null)
        try {
            const response = await openStream(token, controller.signal)

            if (!response.ok) {
                const error = new Error(`SSE stream request failed: ${response.status}`) as Error & {
                    response: { status: number }
                }
                error.response = { status: response.status }
                throw error
            }
            if (!response.body) {
                throw new Error('SSE stream response is empty')
            }

            notificationStreamRuntime.state.isConnecting = false
            notificationStreamRuntime.state.reconnectAttempt = 0
            await consumeSseStream(
                response.body,
                controller.signal,
                (eventType, payload) => handleSseEvent(eventType, payload, sessionGeneration, controller),
            )

            if (!controller.signal.aborted && !notificationStreamRuntime.state.closedManually) {
                throw new Error('SSE stream closed unexpectedly')
            }
        } catch (error: unknown) {
            if (notificationStreamRuntime.state.closedManually || controller.signal.aborted || isAbortError(error)) {
                return
            }

            logger.warn('SSE connection dropped:', error)
            if (error instanceof SseProtocolLimitError) {
                scheduleReconnect(RECONNECT_AFTER_FAILURE_DELAY_MS)
                return
            }
            await reconnectWithRefresh(controller)
        } finally {
            if (notificationStreamRuntime.state.streamAbortController === controller) {
                setNotificationStreamConnection(null)
            }
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
        setNotificationStreamConnection(null)

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
