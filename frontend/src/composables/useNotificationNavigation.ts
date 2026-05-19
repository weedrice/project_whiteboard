import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { useNotification } from '@/composables/useNotification'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import type { Notification } from '@/types'

interface NotificationNavigationOptions {
    showCommentFailureToast?: boolean
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

        if (notification.sourceType === 'POST') {
            try {
                const { data } = await postApi.getPost(notification.sourceId)
                if (data.success && data.data.board) {
                    router.push(`/board/${data.data.board.boardUrl}/post/${notification.sourceId}`)
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
                    const comment = data.data
                    const boardUrl = comment.post?.boardUrl ?? (comment as { boardUrl?: string }).boardUrl
                    const postId = comment.post?.postId ?? (comment as { postId?: number }).postId
                    if (boardUrl && postId) {
                        router.push(`/board/${boardUrl}/post/${postId}#comment-${notification.sourceId}`)
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
