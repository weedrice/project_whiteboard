import type { UserSummary } from './user'

// 댓글 관련 타입
export interface Comment {
    commentId: number
    content: string | null
    author: UserSummary | null
    isBlockedAuthor?: boolean
    maskedAuthorId?: number | null
    parentId?: number
    likeCount: number
    liked?: boolean
    isDeleted: boolean
    children: Comment[]
    replyCount: number
    hasReplies: boolean
    createdAt: string
    modifiedAt?: string
    // Post info when fetching user's comments
    post?: {
        postId: number
        title: string
        boardUrl: string
        boardName: string
    }
}

export type CommentResponse = Comment

export interface CommentListResponse {
    content: Comment[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
    hasPrevious: boolean
}

export interface CommentPayload {
    content: string
    parentId?: number | null
    mentionedUserIds?: number[]
}

export interface MyComment {
    commentId: number
    content: string | null
    post: {
        postId: number
        title: string
        boardUrl: string
        boardName: string
    }
    likeCount: number
    createdAt: string
}
