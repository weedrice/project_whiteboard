export interface MessagePartnerDto {
    userId: number
    displayName: string
}

export interface MessageSummaryDto {
    messageId: number
    content: string
    partner: MessagePartnerDto
    isRead: boolean
    sentByMe?: boolean
    createdAt: string
}

export interface MessageResponse {
    content: MessageSummaryDto[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
    hasPrevious: boolean
}

export interface MailboxMessageViewModel {
    id: number
    partnerUserId: number
    partnerName: string
    body: string
    isUnread: boolean
    sentByMe: boolean
    createdAt: string
}

export type Message = MessageSummaryDto
