import type { MailboxMessageViewModel, MessageResponse } from '@/types'
import { toMailboxMessageViewModel } from '@/features/user/messages/messageViewModel'

export interface ConversationPage {
    messages: MailboxMessageViewModel[]
    page: number
    hasNext: boolean
}

export function mergeConversationMessages(
    ...messageGroups: MailboxMessageViewModel[][]
): MailboxMessageViewModel[] {
    const messagesById = new Map<number, MailboxMessageViewModel>()
    messageGroups.flat().forEach((message) => messagesById.set(message.id, message))
    return Array.from(messagesById.values()).sort((left, right) => {
        const createdAtDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
        return createdAtDifference || left.id - right.id
    })
}

export function toConversationPage(messagePage: MessageResponse, requestedPage: number): ConversationPage {
    const page = Number.isInteger(messagePage.page) ? messagePage.page : requestedPage
    const hasNext = typeof messagePage.hasNext === 'boolean'
        ? messagePage.hasNext
        : page + 1 < (messagePage.totalPages ?? 0)
    return {
        messages: messagePage.content.map(toMailboxMessageViewModel),
        page,
        hasNext,
    }
}
