import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { unwrapApiData } from '@/api/response'
import { useNotification } from '@/composables/useNotification'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import type { Notification } from '@/types'

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

    return `/board/${boardUrl}/post/${postId}`
}

export function mapCommentNotificationRoute(
    comment: NotificationCommentNavigationSource,
    commentId: string | number
): RouteLocationRaw | null {
    const boardUrl = comment.post?.boardUrl ?? comment.boardUrl
    const postId = comment.post?.postId ?? comment.postId
    if (!boardUrl || !postId) return null

    return `/board/${boardUrl}/post/${postId}#comment-${commentId}`
}

function isInternalTargetUrl(targetUrl: string | undefined): targetUrl is string {
    return Boolean(targetUrl?.startsWith('/') && !targetUrl.startsWith('//'))
}

export function useNotificationNavigation(options: NotificationNavigationOptions = {}) {
    const router = useRouter()
    const { t } = useI18n()
    const toastStore = useToastStore()
    const { useMarkAsRead } = useNotification()
    const { mutate: markAsRead } = useMarkAsRead()

    async function navigateFromNotification(notification: Notification) {
        if (!notification.isRead) {
            markAsRead(notification.notificationId)
        }

        if (isInternalTargetUrl(notification.targetUrl)) {
            router.push(notification.targetUrl)
            return
        }

        if (notification.sourceType === 'POST') {
            try {
                const { data } = await postApi.getPost(notification.sourceId)
                if (data.success) {
                    const route = mapPostNotificationRoute(unwrapApiData(data), notification.sourceId)
                    if (route) {
                        router.push(route)
                    }
                }
            } catch (err: unknown) {
                logger.error('Failed to navigate to post:', err)
            }
            return
        }

        if (notification.sourceType === 'COMMENT') {
            try {
                const { data } = await commentApi.getComment(notification.sourceId)
                if (data.success) {
                    const route = mapCommentNotificationRoute(unwrapApiData(data), notification.sourceId)
                    if (route) {
                        router.push(route)
                    }
                }
            } catch (err: unknown) {
                if (options.showCommentFailureToast) {
                    toastStore.addToast(t('common.messages.notFound'), 'warning')
                }
                logger.error('Failed to navigate to comment:', err)
            }
        }
    }

    return {
        navigateFromNotification,
    }
}
