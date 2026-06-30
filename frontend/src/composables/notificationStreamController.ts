import type { QueryClient } from '@tanstack/vue-query'
import { authApi } from '@/api/auth'
import { unwrapAxiosApiData } from '@/api/response'
import {
    notificationApi,
    normalizeNotification,
    type NotificationRaw,
} from '@/api/notification'
import type { Notification, PageResponse } from '@/types'
import logger from '@/utils/logger'
import { useAuthStore } from '@/stores/auth'
import { isCancellationError } from '@/utils/cancellationError'
import {
    notificationsQueryKey,
    notificationUnreadCountQueryKey,
} from '@/composables/notificationQueryKeys'

function isAbortError(error: unknown): boolean {
    return isCancellationError(error, {
        names: ['AbortError'],
        requireDomException: true,
    })
}

function isNotificationPage(data: unknown): data is PageResponse<Notification> {
    if (!data || typeof data !== 'object') return false
    const candidate = data as Partial<PageResponse<Notification>>
    return Array.isArray(candidate.content)
}

function getNotificationPageNumber(data: unknown): number {
    const candidate = data as { number?: unknown; page?: unknown }
    if (typeof candidate.number === 'number') return candidate.number
    if (typeof candidate.page === 'number') return candidate.page
    return 0
}

const RECENT_NOTIFICATION_ID_LIMIT = 200
const RECONNECT_AFTER_REFRESH_DELAY_MS = 1000
const RECONNECT_AFTER_FAILURE_DELAY_MS = 5000
const MAX_RECONNECT_DELAY_MS = 60000

let reconnectCallback: (() => void) | null = null

function isBrowserOnline(): boolean {
    return typeof navigator === 'undefined' ? true : navigator.onLine
}

function getErrorStatus(error: unknown): number | null {
    if (!error || typeof error !== 'object') return null
    const response = (error as { response?: { status?: unknown } }).response
    return typeof response?.status === 'number' ? response.status : null
}

const notificationStreamState = {
    streamAbortController: null as AbortController | null,
    reconnectTimer: null as ReturnType<typeof setTimeout> | null,
    isConnecting: false,
    closedManually: false,
    reconnectAttempt: 0,
    reconnectWhenOnline: false,
    onlineListenerAttached: false,
    recentNotificationIds: new Set<number>(),
}

export function resetNotificationStreamStateForTest() {
    notificationStreamState.closedManually = true

    if (notificationStreamState.reconnectTimer) {
        clearTimeout(notificationStreamState.reconnectTimer)
        notificationStreamState.reconnectTimer = null
    }

    if (notificationStreamState.streamAbortController) {
        notificationStreamState.streamAbortController.abort()
        notificationStreamState.streamAbortController = null
    }

    notificationStreamState.isConnecting = false
    notificationStreamState.reconnectAttempt = 0
    notificationStreamState.reconnectWhenOnline = false
    if (typeof window !== 'undefined' && notificationStreamState.onlineListenerAttached) {
        window.removeEventListener('online', handleBrowserOnline)
    }
    notificationStreamState.onlineListenerAttached = false
    reconnectCallback = null
    notificationStreamState.recentNotificationIds.clear()
}

function handleBrowserOnline() {
    if (notificationStreamState.closedManually || !notificationStreamState.reconnectWhenOnline) return
    notificationStreamState.reconnectWhenOnline = false
    scheduleReconnect(0)
}

function attachOnlineReconnectListener() {
    if (typeof window === 'undefined' || notificationStreamState.onlineListenerAttached) return
    window.addEventListener('online', handleBrowserOnline)
    notificationStreamState.onlineListenerAttached = true
}

function detachOnlineReconnectListener() {
    if (typeof window === 'undefined' || !notificationStreamState.onlineListenerAttached) return
    window.removeEventListener('online', handleBrowserOnline)
    notificationStreamState.onlineListenerAttached = false
}

function getBackoffDelay(baseDelayMs: number) {
    const multiplier = 2 ** notificationStreamState.reconnectAttempt
    return Math.min(baseDelayMs * multiplier, MAX_RECONNECT_DELAY_MS)
}

function scheduleReconnect(delayMs: number) {
    if (notificationStreamState.closedManually) return

    if (!isBrowserOnline()) {
        notificationStreamState.reconnectWhenOnline = true
        attachOnlineReconnectListener()
        return
    }

    detachOnlineReconnectListener()

    if (notificationStreamState.reconnectTimer) {
        clearTimeout(notificationStreamState.reconnectTimer)
    }

    const backoffDelayMs = getBackoffDelay(delayMs)
    notificationStreamState.reconnectAttempt += 1
    notificationStreamState.reconnectTimer = setTimeout(() => {
        notificationStreamState.reconnectTimer = null
        reconnectCallback?.()
    }, backoffDelayMs)
}

export function createNotificationStreamController(queryClient: QueryClient) {
    const rememberNotificationId = (notificationId: number) => {
        notificationStreamState.recentNotificationIds.add(notificationId)
        if (notificationStreamState.recentNotificationIds.size > RECENT_NOTIFICATION_ID_LIMIT) {
            const oldestId = notificationStreamState.recentNotificationIds.values().next().value
            if (typeof oldestId === 'number') {
                notificationStreamState.recentNotificationIds.delete(oldestId)
            }
        }
    }

    const applyIncomingNotification = (incoming: Notification) => {
        const normalized: Notification = {
            ...incoming,
            isRead: false,
        }
        const notificationId = normalized.notificationId
        if (typeof notificationId === 'number' && notificationStreamState.recentNotificationIds.has(notificationId)) {
            return
        }

        let alreadyExistsInFirstPage = false

        queryClient.setQueriesData({ queryKey: notificationsQueryKey }, (oldData: unknown) => {
            if (!isNotificationPage(oldData)) return oldData
            if (getNotificationPageNumber(oldData) !== 0) return oldData

            const alreadyExists = oldData.content.some((item) => item.notificationId === normalized.notificationId)
            if (alreadyExists) {
                alreadyExistsInFirstPage = true
                return oldData
            }

            const nextContent = [normalized, ...oldData.content]
            const sizeLimit = oldData.size > 0 ? oldData.size : nextContent.length

            return {
                ...oldData,
                content: nextContent.slice(0, sizeLimit),
                totalElements: oldData.totalElements + 1,
                empty: false,
            }
        })

        if (alreadyExistsInFirstPage) {
            if (typeof notificationId === 'number') {
                rememberNotificationId(notificationId)
            }
            return
        }

        if (typeof notificationId === 'number') {
            rememberNotificationId(notificationId)
        }

        queryClient.setQueryData(notificationUnreadCountQueryKey, (old: number | undefined) => (old || 0) + 1)
    }

    const handleSseEvent = (eventType: string, payload: string) => {
        if (!payload) return
        if (eventType !== 'notification' && eventType !== 'message') return

        try {
            const rawNotification = JSON.parse(payload) as NotificationRaw
            const rawNotificationId = rawNotification.notificationId ?? rawNotification.notification_id
            if (typeof rawNotificationId !== 'number' || !Number.isFinite(rawNotificationId)) return
            const notification = normalizeNotification(rawNotification)
            applyIncomingNotification(notification)
        } catch (error: unknown) {
            logger.error('Failed to parse SSE notification:', error)
        }
    }

    const consumeSseStream = async (stream: ReadableStream<Uint8Array>, signal: AbortSignal) => {
        const reader = stream.getReader()
        const decoder = new TextDecoder()

        let buffer = ''
        let currentEvent = 'message'
        let dataLines: string[] = []

        const flushEvent = () => {
            const payload = dataLines.join('\n').trim()
            if (payload) {
                handleSseEvent(currentEvent, payload)
            }
            currentEvent = 'message'
            dataLines = []
        }

        try {
            while (!signal.aborted) {
                const { done, value } = await reader.read()
                if (done) break

                buffer += decoder.decode(value, { stream: true })

                let newlineIndex = buffer.indexOf('\n')
                while (newlineIndex !== -1) {
                    const rawLine = buffer.slice(0, newlineIndex)
                    buffer = buffer.slice(newlineIndex + 1)
                    const line = rawLine.replace(/\r$/, '')

                    if (line === '') {
                        flushEvent()
                    } else if (line.startsWith(':')) {
                        // Keep-alive comment; ignore.
                    } else if (line.startsWith('event:')) {
                        currentEvent = line.slice(6).trim() || 'message'
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.slice(5).trimStart())
                    }

                    newlineIndex = buffer.indexOf('\n')
                }
            }

            if (buffer.trim() || dataLines.length > 0) {
                if (buffer.startsWith('data:')) {
                    dataLines.push(buffer.slice(5).trimStart())
                }
                flushEvent()
            }
        } finally {
            await reader.cancel().catch(() => undefined)
        }
    }

    const reconnectWithRefresh = async () => {
        if (!isBrowserOnline()) {
            scheduleReconnect(RECONNECT_AFTER_FAILURE_DELAY_MS)
            return
        }

        try {
            const authTokens = unwrapAxiosApiData(await authApi.refreshToken({
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            }))
            const authStore = useAuthStore()
            authStore.setTokens(authTokens.accessToken)
            scheduleReconnect(RECONNECT_AFTER_REFRESH_DELAY_MS)
            return
        } catch (error: unknown) {
            logger.warn('SSE reconnect: refresh failed', error)
            const status = getErrorStatus(error)
            if (status === 401 || status === 403) {
                notificationStreamState.closedManually = true
                return
            }
        }

        scheduleReconnect(RECONNECT_AFTER_FAILURE_DELAY_MS)
    }

    const startStream = async (token: string, controller: AbortController) => {
        try {
            const response = await notificationApi.openStream(token, controller.signal)

            if (!response.ok) {
                throw new Error(`SSE stream request failed: ${response.status}`)
            }
            if (!response.body) {
                throw new Error('SSE stream response is empty')
            }

            notificationStreamState.isConnecting = false
            notificationStreamState.reconnectAttempt = 0
            await consumeSseStream(response.body, controller.signal)

            if (!controller.signal.aborted && !notificationStreamState.closedManually) {
                throw new Error('SSE stream closed unexpectedly')
            }
        } catch (error: unknown) {
            if (notificationStreamState.closedManually || controller.signal.aborted || isAbortError(error)) {
                return
            }

            logger.warn('SSE connection dropped:', error)
            await reconnectWithRefresh()
        } finally {
            if (notificationStreamState.streamAbortController === controller) {
                notificationStreamState.streamAbortController = null
            }
            notificationStreamState.isConnecting = false
        }
    }

    const connectToSse = () => {
        if (notificationStreamState.streamAbortController || notificationStreamState.isConnecting) return

        if (notificationStreamState.reconnectTimer) {
            clearTimeout(notificationStreamState.reconnectTimer)
            notificationStreamState.reconnectTimer = null
        }
        detachOnlineReconnectListener()
        notificationStreamState.reconnectWhenOnline = false

        const authStore = useAuthStore()
        const token = authStore.accessToken
        if (!token) return

        notificationStreamState.closedManually = false
        notificationStreamState.isConnecting = true

        const controller = new AbortController()
        notificationStreamState.streamAbortController = controller

        void startStream(token, controller)
    }

    reconnectCallback = connectToSse

    const closeSse = () => {
        notificationStreamState.closedManually = true

        if (notificationStreamState.reconnectTimer) {
            clearTimeout(notificationStreamState.reconnectTimer)
            notificationStreamState.reconnectTimer = null
        }

        if (notificationStreamState.streamAbortController) {
            notificationStreamState.streamAbortController.abort()
            notificationStreamState.streamAbortController = null
        }

        notificationStreamState.isConnecting = false
        notificationStreamState.reconnectAttempt = 0
        notificationStreamState.reconnectWhenOnline = false
        detachOnlineReconnectListener()
    }

    return {
        connectToSse,
        closeSse,
    }
}
