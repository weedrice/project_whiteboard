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

const notificationStreamState = {
    streamAbortController: null as AbortController | null,
    reconnectTimer: null as ReturnType<typeof setTimeout> | null,
    isConnecting: false,
    closedManually: false,
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
    notificationStreamState.recentNotificationIds.clear()
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

    const scheduleReconnect = (delayMs: number) => {
        if (notificationStreamState.closedManually) return
        if (notificationStreamState.reconnectTimer) {
            clearTimeout(notificationStreamState.reconnectTimer)
        }
        notificationStreamState.reconnectTimer = setTimeout(() => {
            notificationStreamState.reconnectTimer = null
            connectToSse()
        }, delayMs)
    }

    const reconnectWithRefresh = async () => {
        try {
            const authTokens = unwrapAxiosApiData(await authApi.refreshToken())
            const authStore = useAuthStore()
            authStore.setTokens(authTokens.accessToken)
            scheduleReconnect(1000)
            return
        } catch (error: unknown) {
            logger.warn('SSE reconnect: refresh failed', error)
        }

        scheduleReconnect(5000)
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

        const authStore = useAuthStore()
        const token = authStore.accessToken
        if (!token) return

        notificationStreamState.closedManually = false
        notificationStreamState.isConnecting = true

        const controller = new AbortController()
        notificationStreamState.streamAbortController = controller

        void startStream(token, controller)
    }

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
    }

    return {
        connectToSse,
        closeSse,
    }
}
