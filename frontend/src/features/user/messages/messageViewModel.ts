import type { MailboxMessageViewModel, MessageSummaryDto } from '@/types'

export function toMailboxMessageViewModel(message: MessageSummaryDto): MailboxMessageViewModel {
    return {
        id: message.messageId,
        partnerUserId: message.partner.userId,
        partnerName: message.partner.displayName,
        body: message.content,
        isUnread: !message.isRead,
        createdAt: message.createdAt,
    }
}

export function markMailboxMessageRead(message: MailboxMessageViewModel): MailboxMessageViewModel {
    return {
        ...message,
        isUnread: false,
    }
}
