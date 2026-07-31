import { onScopeDispose } from 'vue'
import type { Notification } from '@/types'
import { subscribeMessageStreamEvents } from '@/features/user/messages/messageStreamEvents'

interface MailboxRealtimeSyncOptions {
    isConversationOpen: () => boolean
    getConversationPartnerId: () => number | null
    deferMailboxRefresh: () => void
    refreshMailbox: () => void
    refreshConversation: (partnerId: number) => void
    incrementPendingConversationMessages: () => void
}

export function useMailboxRealtimeSync(options: MailboxRealtimeSyncOptions) {
    const recentNotificationIds = new Set<number>()

    function remember(notificationId: number) {
        if (recentNotificationIds.has(notificationId)) return false
        recentNotificationIds.add(notificationId)
        if (recentNotificationIds.size > 100) {
            const oldestNotificationId = recentNotificationIds.values().next().value
            if (oldestNotificationId != null) recentNotificationIds.delete(oldestNotificationId)
        }
        return true
    }

    function handleMessageStreamEvent(notification: Notification) {
        if (!remember(notification.notificationId)) return

        const partnerId = notification.actor.userId
        const currentPartnerId = options.getConversationPartnerId()
        if (options.isConversationOpen()) {
            options.deferMailboxRefresh()
            if (partnerId > 0 && partnerId === currentPartnerId) {
                options.incrementPendingConversationMessages()
            }
            return
        }

        options.refreshMailbox()
        if (partnerId > 0 && partnerId === currentPartnerId) {
            options.refreshConversation(partnerId)
        }
    }

    const unsubscribe = subscribeMessageStreamEvents(handleMessageStreamEvent)

    function reset() {
        recentNotificationIds.clear()
    }

    function stop() {
        unsubscribe()
        reset()
    }

    onScopeDispose(stop)

    return {
        reset,
        stop,
    }
}
