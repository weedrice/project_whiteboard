import { useRouter, type RouteLocationRaw } from 'vue-router'
import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { messageApi } from '@/api/message'
import { unwrapApiData } from '@/api/response'
import { useNotification } from '@/features/notifications/queries/useNotification'
import logger from '@/utils/logger'
import { buildPostDetailPath, encodePathSegment, normalizePostDetailPath } from '@/utils/urlPath'
import type { Notification } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'

interface NotificationPostNavigationSource {
    board?: {
        boardUrl?: string | null
    } | null
}

interface NotificationCommentNavigationSource {
    post?: {
        boardUrl?: string | null
        postId?: string | number | null
    } | null
    boardUrl?: string | null
    postId?: string | number | null
}

export function mapPostNotificationRoute(
    post: NotificationPostNavigationSource,
    postId: string | number
): RouteLocationRaw | null {
    const boardUrl = post.board?.boardUrl
    if (!boardUrl) return null

    return buildPostDetailPath(boardUrl, postId)
}

export function mapCommentNotificationRoute(
    comment: NotificationCommentNavigationSource,
    commentId: string | number
): RouteLocationRaw | null {
    const boardUrl = comment.post?.boardUrl ?? comment.boardUrl
    const postId = comment.post?.postId ?? comment.postId
    if (!boardUrl || !postId) return null

    return buildPostDetailPath(boardUrl, postId, `#comment-${encodePathSegment(commentId)}`)
}

function isInternalTargetUrl(targetUrl: string | undefined): targetUrl is string {
    return Boolean(targetUrl?.startsWith('/') && !targetUrl.startsWith('//'))
}

export function useNotificationNavigation() {
    const router = useRouter()
    const { t } = useI18n()
    const authStore = useAuthStore()
    const { useMarkAsRead } = useNotification()
    const { mutate: markAsRead } = useMarkAsRead()
    const isNavigating = ref(false)
    const navigationErrorKey = ref('')
    const navigationError = computed(() => navigationErrorKey.value === 'notification.navigationUnavailable'
        ? t('notification.navigationUnavailable')
        : navigationErrorKey.value ? t('notification.navigationFailed') : '')
    let failedNotification: Notification | null = null
    let navigationRevision = 0
    let navigationController: AbortController | null = null

    const cancelPendingNavigation = () => {
        navigationRevision += 1
        navigationController?.abort()
        navigationController = null
        isNavigating.value = false
        navigationErrorKey.value = ''
        failedNotification = null
    }
    if (getCurrentScope()) {
        const stopSessionBoundary = subscribeAuthSessionBoundary(cancelPendingNavigation)
        onScopeDispose(() => {
            cancelPendingNavigation()
            stopSessionBoundary()
        })
    }

    async function navigateFromNotification(notification: Notification) {
        cancelPendingNavigation()
        const controller = new AbortController()
        navigationController = controller
        const revision = navigationRevision
        const generation = authStore.sessionGeneration
        const isCurrent = () => navigationRevision === revision
            && navigationController === controller
            && !controller.signal.aborted
            && authStore.sessionGeneration === generation
        if (!notification.isRead) {
            markAsRead(notification.notificationId)
        }

        const failNavigation = (unavailable: boolean) => {
            navigationErrorKey.value = unavailable
                ? 'notification.navigationUnavailable'
                : 'notification.navigationFailed'
            failedNotification = notification
        }
        isNavigating.value = true
        try {
            // Generic mailbox links need their source to identify the conversation.
            const needsMessageSourceLookup = notification.sourceType === 'MESSAGE'
                && notification.targetUrl === '/mypage/messages'
            let target: RouteLocationRaw | null = null
            if (!needsMessageSourceLookup && isInternalTargetUrl(notification.targetUrl)) {
                target = normalizePostDetailPath(notification.targetUrl)
            } else {
                const config = { signal: controller.signal, skipGlobalErrorHandler: true }
                if (notification.sourceType === 'POST') {
                    const { data } = await postApi.getPost(notification.sourceId, config)
                    if (!isCurrent()) return
                    const post = data.success ? unwrapApiData(data) : null
                    target = post ? mapPostNotificationRoute(post, notification.sourceId) : null
                } else if (notification.sourceType === 'COMMENT') {
                    const { data } = await commentApi.getComment(notification.sourceId, config)
                    if (!isCurrent()) return
                    const comment = data.success ? unwrapApiData(data) : null
                    target = comment ? mapCommentNotificationRoute(comment, notification.sourceId) : null
                } else if (notification.sourceType === 'MESSAGE') {
                    const { data } = await messageApi.getMessage(notification.sourceId, config)
                    if (!isCurrent()) return
                    const message = data.success ? unwrapApiData(data) : null
                    if (message?.partner?.userId) {
                        target = { name: 'MyMessages', query: { partnerId: String(message.partner.userId) } }
                    }
                } else {
                    // Informational system notifications can intentionally have no destination.
                    return
                }
            }
            if (!isCurrent()) return
            if (target) {
                await router.push(target)
            } else {
                failNavigation(true)
            }
        } catch (error: unknown) {
            if (!isCurrent() || isCancellationError(error)) return
            const status = (error as { response?: { status?: number } })?.response?.status
            failNavigation(status === 403 || status === 404 || status === 410)
            logger.error('Failed to navigate from notification:', error)
        } finally {
            if (isCurrent()) isNavigating.value = false
        }
    }

    async function retryNavigation() {
        if (failedNotification && !isNavigating.value) {
            await navigateFromNotification(failedNotification)
        }
    }

    return {
        isNavigating,
        navigationError,
        retryNavigation,
        cancelPendingNavigation,
        navigateFromNotification,
    }
}
