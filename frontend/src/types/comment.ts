import type { UserSummary } from './user'

// 댓글 관련 타입
export interface Comment {
    commentId: number
    content: string
    author: UserSummary
    parentId?: number
    likeCount: number
    liked?: boolean
    isDeleted: boolean
    children?: Comment[]
    createdAt: string
    modifiedAt?: string
}

export type CommentResponse = Comment

export interface CommentPayload {
    content: string
    parentId?: number | null
}
