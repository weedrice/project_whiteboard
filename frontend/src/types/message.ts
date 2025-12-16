import type { UserSummary } from './user'

// 메시지 관련 타입
export interface Message {
    messageId: number
    content: string
    partner: UserSummary
    read: boolean
    createdAt: string
}
