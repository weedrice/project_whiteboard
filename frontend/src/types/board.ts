import type { UserSummary } from './user'

export interface BoardListItem {
    boardId: number
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder: number
    subscriberCount: number
    adminDisplayName?: string
    isSubscribed: boolean
    isActive: boolean
    isPublic: boolean
    subscriptionAccessible: boolean
}

export interface SubscriptionBoardListItem {
    boardId: number
    boardName: string | null
    boardUrl: string
    description?: string | null
    iconUrl?: string | null
    sortOrder: number
    subscriberCount: number
    adminDisplayName?: string | null
    isSubscribed: boolean
    isActive: boolean
    isPublic: boolean
    subscriptionAccessible: boolean
}

export interface BoardDetail extends BoardListItem {
    allowNsfw: boolean
    isAdmin: boolean
    categories: Category[]
    latestPosts: PostSummary[]
    adminUserId?: number
    agentUseYn: boolean
    guidePrompt?: string
}

export interface AdminBoard {
    boardId: number
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder: number
    adminDisplayName?: string
    adminUserId?: number
    allowNsfw: boolean
    isActive: boolean
    isPublic: boolean
    agentUseYn: boolean
    guidePrompt?: string
}

export interface BoardSearchItem {
    boardId: number
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
}

export interface BoardCreateData {
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    isPublic?: boolean
    agentUseYn?: boolean
    guidePrompt?: string
}

export interface BoardUpdateData {
    boardName?: string
    boardUrl?: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    isActive?: boolean
    isPublic?: boolean
    agentUseYn?: boolean
    guidePrompt?: string
}

export interface Category {
    categoryId: number
    name: string
    sortOrder: number
    isActive: boolean
    minWriteRole: string
}

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
    isSecret?: boolean
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
    rowNum?: number
    postId: number
    title: string
    viewCount: number
    likeCount: number
    commentCount: number
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    isSecret?: boolean
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
    inquiryAnswered?: boolean
    summary?: string
    contentsExcerpt?: string
    firstMediaType?: string
    firstMediaUrl?: string
}

export interface FeedPost extends Omit<PostSummary, 'liked' | 'scrapped' | 'subscribed' | 'boardUrl' | 'boardName' | 'authorName'> {
    boardUrl: string | number
    boardName: string
    boardIconUrl?: string
    authorName: string
    liked: boolean
    scrapped: boolean
    subscribed: boolean
    summary?: string
    contentsExcerpt?: string
    firstMediaType?: string
    firstMediaUrl?: string
}

export type HomeLandingPeriod = '24h' | '7d' | '30d'

export interface HomeLandingStats {
    boardCount: number
    postCount: number
    liveCount: number
    onlineCount: number
    postsToday: number
    postsTodayDeltaPercent: number | null
    activeBoardCount: number
    newMembersLast24Hours: number
    commentsToday: number
}

export interface HomeLandingResponse {
    featuredPost: PostSummary | null
    editorPicks: PostSummary[]
    trendingPosts: PostSummary[]
    liveActivity: PostSummary[]
    boards: BoardListItem[]
    stats: HomeLandingStats
}

export interface DraftPostSummary {
    draftId: number
    title?: string
    boardId: number
    boardUrl: string
    boardName: string
    originalPostId?: number | null
    updatedAt?: string
    modifiedAt?: string
}

export interface DraftPostListResponse {
    content: DraftPostSummary[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
    hasPrevious: boolean
}

export interface DraftPost {
    draftId: number
    boardId: number
    boardUrl: string
    boardName: string
    title?: string
    contents?: string
    categoryId?: number | null
    tags: string[]
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    isSecret: boolean
    fileIds: number[]
    originalPostId?: number | null
    updatedAt?: string
    modifiedAt?: string
}
