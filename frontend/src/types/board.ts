import type { UserSummary } from './user'

// 게시판 관련 타입
export interface Board {
    boardId: number
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder: number
    isActive: boolean
    allowNsfw: boolean
    subscriberCount?: number
    isAdmin?: boolean
    isSubscribed?: boolean
    createdAt: string
    modifiedAt?: string
    categories?: Category[]
    adminUserId?: number
    adminDisplayName?: string
}

export interface BoardCreateData {
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
}

export interface BoardUpdateData {
    boardName?: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    isActive?: boolean
}

export interface Category {
    categoryId: number
    name: string
    sortOrder: number
    isActive: boolean
    minWriteRole: string
}

// 게시글 관련 타입
export interface Post {
    postId: number
    title: string
    contents: string
    viewCount: number
    likeCount: number
    commentCount: number
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    author: UserSummary
    board: {
        boardId: number
        boardName: string
        boardUrl: string
        iconUrl?: string
        isAdmin?: boolean
    }
    category?: Category
    tags?: string[]
    liked?: boolean
    scrapped?: boolean
    createdAt: string
    modifiedAt?: string
}

export interface PostSummary {
    postId: number
    title: string
    viewCount: number
    likeCount: number
    commentCount: number
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    author: UserSummary
    category?: Category
    thumbnailUrl?: string
    createdAt: string
    liked?: boolean
    scrapped?: boolean
    subscribed?: boolean
    boardUrl?: string
    boardName?: string
    boardIconUrl?: string
    authorName?: string
    summary?: string
}

// FeedPost extends PostSummary with required feed-specific fields
export interface FeedPost extends Omit<PostSummary, 'liked' | 'scrapped' | 'subscribed' | 'boardUrl' | 'boardName' | 'authorName'> {
    boardUrl: string | number
    boardName: string
    boardIconUrl?: string
    authorName: string
    liked: boolean
    scrapped: boolean
    subscribed: boolean
    summary?: string
}
