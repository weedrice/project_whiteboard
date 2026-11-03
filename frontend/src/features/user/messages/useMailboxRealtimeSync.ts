import { onScopeDispose } from 'vue'
import { subscribeMessageStreamEvents, type MailboxStreamEvent } from '@/features/user/messages/messageStreamEvents'

interface MailboxRealtimeSyncOptions {
    isConversationOpen: () => boolean
    getConversationPartnerId: () => number | null
    deferMailboxRefresh: () => void
    refreshMailbox: () => void
    refreshConversation: (partnerId: number) => void
    incrementPendingConversationMessages: () => void
}

export function useMailboxRealtimeSync(options: MailboxRealtimeSyncOptions) {
    const recentMessageKeys = new Set<string>()

    function remember(key: string) {
        if (recentMessageKeys.has(key)) return false
        recentMessageKeys.add(key)
        if (recentMessageKeys.size > 100) {
            const oldestKey = recentMessageKeys.values().next().value
            if (oldestKey != null) recentMessageKeys.delete(oldestKey)
        }
        return true
    }

    function handleMessageStreamEvent(event: MailboxStreamEvent) {
        const isMailboxChange = 'messageId' in event
        const messageId = isMailboxChange ? event.messageId : event.sourceId
        const key = Number.isSafeInteger(messageId) && Number(messageId) > 0
            ? `message:${messageId}`
            : `notification:${isMailboxChange ? '' : event.notificationId}`
        if (!remember(key)) return

        const partnerId = isMailboxChange ? event.partnerId : event.actor.userId
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
        recentMessageKeys.clear()
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
