import type { UserSummary } from './user'

export interface CommentMention {
    userId: number
    displayName: string
    profileImageUrl?: string | null
}

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
    isBlinded?: boolean
    blindReason?: string | null
    children: Comment[]
    mentions?: CommentMention[]
    replyCount: number
    hasReplies: boolean
    createdAt: string
    modifiedAt?: string
    // Direct post metadata returned by integrated search results.
    postId?: number
    boardUrl?: string
    postTitle?: string
    depth?: number
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
