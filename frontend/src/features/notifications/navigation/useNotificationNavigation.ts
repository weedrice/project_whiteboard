import { useRouter, type RouteLocationRaw } from 'vue-router'
import { getCurrentScope, onScopeDispose } from 'vue'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { messageApi } from '@/api/message'
import { unwrapApiData } from '@/api/response'
import { useNotification } from '@/features/notifications/queries/useNotification'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { encodePathSegment } from '@/utils/urlPath'
import type { Notification } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'

interface NotificationNavigationOptions {
    showCommentFailureToast?: boolean
}

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

    return `/board/${encodePathSegment(boardUrl)}/post/${encodePathSegment(postId)}`
}

export function mapCommentNotificationRoute(
    comment: NotificationCommentNavigationSource,
    commentId: string | number
): RouteLocationRaw | null {
    const boardUrl = comment.post?.boardUrl ?? comment.boardUrl
    const postId = comment.post?.postId ?? comment.postId
    if (!boardUrl || !postId) return null

    return `/board/${encodePathSegment(boardUrl)}/post/${encodePathSegment(postId)}#comment-${encodePathSegment(commentId)}`
}

function isInternalTargetUrl(targetUrl: string | undefined): targetUrl is string {
    return Boolean(targetUrl?.startsWith('/') && !targetUrl.startsWith('//'))
}

export function useNotificationNavigation(options: NotificationNavigationOptions = {}) {
    const router = useRouter()
    const { t } = useI18n()
    const toastStore = useToastStore()
    const authStore = useAuthStore()
    const { useMarkAsRead } = useNotification()
    const { mutate: markAsRead } = useMarkAsRead()
    let navigationRevision = 0
    let navigationController: AbortController | null = null

    const cancelPendingNavigation = () => {
        navigationRevision += 1
        navigationController?.abort()
        navigationController = null
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

        if (isInternalTargetUrl(notification.targetUrl)) {
            if (isCurrent()) router.push(notification.targetUrl)
            return
        }

        if (notification.sourceType === 'POST') {
            try {
                const { data } = await postApi.getPost(notification.sourceId, {
                    signal: controller.signal,
                    skipGlobalErrorHandler: true,
                })
                if (isCurrent() && data.success) {
                    const route = mapPostNotificationRoute(unwrapApiData(data), notification.sourceId)
                    if (route) {
                        router.push(route)
                    }
                }
            } catch (err: unknown) {
                if (!isCurrent() || isCancellationError(err)) return
                logger.error('Failed to navigate to post:', err)
            }
            return
        }

        if (notification.sourceType === 'COMMENT') {
            try {
                const { data } = await commentApi.getComment(notification.sourceId, {
                    signal: controller.signal,
                    skipGlobalErrorHandler: true,
                })
                if (isCurrent() && data.success) {
                    const route = mapCommentNotificationRoute(unwrapApiData(data), notification.sourceId)
                    if (route) {
                        router.push(route)
                    }
                }
            } catch (err: unknown) {
                if (!isCurrent() || isCancellationError(err)) return
                if (options.showCommentFailureToast) {
                    toastStore.addToast(t('common.messages.notFound'), 'warning')
                }
                logger.error('Failed to navigate to comment:', err)
            }
            return
        }

        if (notification.sourceType === 'MESSAGE') {
            try {
                const { data } = await messageApi.getMessage(notification.sourceId, {
                    signal: controller.signal,
                    skipGlobalErrorHandler: true,
                })
                if (isCurrent() && data.success) {
                    const message = unwrapApiData(data)
                    if (message?.partner?.userId) {
                        router.push({
                            name: 'MyMessages',
                            query: { partnerId: String(message.partner.userId) },
                        })
                    }
                }
            } catch (err: unknown) {
                if (!isCurrent() || isCancellationError(err)) return
                logger.error('Failed to navigate to message:', err)
            }
        }
    }

    return {
        cancelPendingNavigation,
        navigateFromNotification,
    }
}
