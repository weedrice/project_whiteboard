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
}
