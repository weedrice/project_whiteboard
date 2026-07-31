import { describe, expect, it } from 'vitest'
import type { MailboxMessageViewModel, MessageResponse, MessageSummaryDto } from '@/types'
import { mergeConversationMessages, toConversationPage } from '../conversationModel'

const message = (id: number, createdAt: string, body = `Message ${id}`): MailboxMessageViewModel => ({
    id,
    partnerUserId: 100,
    partnerName: 'Partner',
    body,
    isUnread: false,
    sentByMe: false,
    createdAt,
})

const messageDto = (id: number): MessageSummaryDto => ({
    messageId: id,
    content: `Message ${id}`,
    partner: { userId: 100, displayName: 'Partner' },
    isRead: true,
    createdAt: '2026-07-31T00:00:00',
})

describe('conversationModel', () => {
    it('deduplicates messages by id and sorts them chronologically', () => {
        const result = mergeConversationMessages(
            [message(2, '2026-07-31T02:00:00'), message(1, '2026-07-31T01:00:00', 'old')],
            [message(1, '2026-07-31T01:00:00', 'updated'), message(3, '2026-07-31T02:00:00')],
        )

        expect(result.map(({ id }) => id)).toEqual([1, 2, 3])
        expect(result[0]?.body).toBe('updated')
    })

    it('uses response pagination metadata when it is available', () => {
        const page = toConversationPage({
            content: [messageDto(1)],
            page: 2,
            size: 20,
            totalElements: 80,
            totalPages: 4,
            hasNext: true,
            hasPrevious: true,
        }, 1)

        expect(page).toMatchObject({ page: 2, hasNext: true })
        expect(page.messages.map(({ id }) => id)).toEqual([1])
    })

    it('falls back to the requested page and total page count for legacy responses', () => {
        const response = {
            content: [],
            page: Number.NaN,
            size: 20,
            totalElements: 60,
            totalPages: 3,
            hasPrevious: false,
        } as unknown as MessageResponse

        expect(toConversationPage(response, 1)).toEqual({ messages: [], page: 1, hasNext: true })
    })
})
