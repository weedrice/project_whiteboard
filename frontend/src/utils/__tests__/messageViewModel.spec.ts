import { describe, expect, it } from 'vitest'
import { markMailboxMessageRead, toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'

describe('messageViewModel', () => {
    it('maps backend message summary dto to mailbox view model', () => {
        expect(toMailboxMessageViewModel({
            messageId: 7,
            content: 'hello',
            partner: {
                userId: 3,
                displayName: 'Other',
            },
            isRead: false,
            createdAt: '2026-04-16T11:00:00',
        })).toEqual({
            id: 7,
            partnerUserId: 3,
            partnerName: 'Other',
            body: 'hello',
            isUnread: true,
            sentByMe: false,
            createdAt: '2026-04-16T11:00:00',
        })
    })

    it('marks a mailbox message view model as read immutably', () => {
        const message = {
            id: 7,
            partnerUserId: 3,
            partnerName: 'Other',
            body: 'hello',
            isUnread: true,
            sentByMe: false,
            createdAt: '2026-04-16T11:00:00',
        }

        const readMessage = markMailboxMessageRead(message)

        expect(readMessage).toEqual({ ...message, isUnread: false })
        expect(readMessage).not.toBe(message)
        expect(message.isUnread).toBe(true)
    })
})
