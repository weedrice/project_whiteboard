import { effectScope } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { Notification } from '@/types'
import { emitMessageChangedEvent, emitMessageStreamEvent } from '../messageStreamEvents'
import { useMailboxRealtimeSync } from '../useMailboxRealtimeSync'

describe('mailbox realtime sync across old and new servers', () => {
    it.each([true, false])('counts a message only once in either event order (change first: %s)', (changeFirst) => {
        const scope = effectScope()
        const increment = vi.fn()
        const refresh = vi.fn()
        const defer = vi.fn()
        const sync = scope.run(() => useMailboxRealtimeSync({
            isConversationOpen: () => true,
            getConversationPartnerId: () => 2,
            deferMailboxRefresh: defer,
            refreshMailbox: refresh,
            refreshConversation: vi.fn(),
            incrementPendingConversationMessages: increment,
        }))!
        const changed = () => emitMessageChangedEvent({ messageId: 8, partnerId: 2 })
        const notification = () => emitMessageStreamEvent({
            notificationId: 91, notificationType: 'MESSAGE', sourceType: 'MESSAGE', sourceId: 8,
            actor: { userId: 2 },
        } as Notification)
        try {
            if (changeFirst) { changed(); notification() } else { notification(); changed() }
            changed()
            expect(increment).toHaveBeenCalledTimes(1)
            expect(defer).toHaveBeenCalledTimes(1)
            expect(refresh).not.toHaveBeenCalled()
            sync.reset()
            changed()
            expect(increment).toHaveBeenCalledTimes(2)
        } finally {
            scope.stop()
        }
    })

    it('refreshes the mailbox without requiring a notification and stops on disposal', () => {
        const scope = effectScope()
        const refresh = vi.fn()
        scope.run(() => useMailboxRealtimeSync({
            isConversationOpen: () => false,
            getConversationPartnerId: () => null,
            deferMailboxRefresh: vi.fn(),
            refreshMailbox: refresh,
            refreshConversation: vi.fn(),
            incrementPendingConversationMessages: vi.fn(),
        }))
        emitMessageChangedEvent({ messageId: 8, partnerId: 2 })
        expect(refresh).toHaveBeenCalledTimes(1)
        scope.stop()
        emitMessageChangedEvent({ messageId: 9, partnerId: 2 })
        expect(refresh).toHaveBeenCalledTimes(1)
    })
})
