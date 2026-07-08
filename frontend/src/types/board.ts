import type { UserSummary } from './user'

export interface BoardListItem {
    boardId: number
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder: number
    subscriberCount: number
    postCount: number
    adminDisplayName?: string
    isSubscribed: boolean
    isActive: boolean
    isPublic: boolean
    subscriptionAccessible: boolean
}

export type SubscriptionAccessState = 'ACCESSIBLE' | 'INACCESSIBLE'
export type SubscriptionInaccessibleReason = 'INACTIVE' | 'PRIVATE' | 'RESTRICTED'

export interface SubscriptionBoardListItem {
    boardId: number
    boardName: string | null
    boardUrl: string
    description?: string | null
    iconUrl?: string | null
    sortOrder: number
    subscriberCount: number
    postCount: number
    adminDisplayName?: string | null
    latestPostAt?: string | null
    newPostCount: number
    hasNewPosts: boolean
    isSubscribed: boolean
    isActive: boolean
    isPublic: boolean
    subscriptionAccessible: boolean
    accessState: SubscriptionAccessState
    inaccessibleReason?: SubscriptionInaccessibleReason | null
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

export interface BoardManagerCandidate {
    userId: number
    loginId: string
    displayName: string
    profileImageUrl?: string | null
    currentManager: boolean
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
    isDefault?: boolean
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
    lastViewedAt?: string | null
    poll?: PostPoll | null
    seriesNavigation?: PostSeriesNavigation | null
    boardListPage?: number
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
    hasImage?: boolean
    summary?: string
}

export interface FeedPost {
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
    boardUrl: string | number
    boardName: string
    boardIconUrl?: string
    authorName: string
    liked: boolean
    scrapped: boolean
    subscribed: boolean
    hasImage?: boolean
    summary?: string
    contentsExcerpt?: string
    firstMediaType?: string
    firstMediaUrl?: string
}

export interface AdminInquirySummary {
    postId: number
    title: string
    summary?: string
    author: UserSummary
    createdAt: string
    inquiryAnswered?: boolean
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

export interface PostSeriesNavigation {
    series?: {
        seriesId: number
        title: string
    } | null
    previousPost?: PostSeriesLink | null
    nextPost?: PostSeriesLink | null
}

export interface PostSeriesLink {
    postId: number
    title: string
    boardUrl: string
}

export interface BoardRecentUpdate {
    boardUrl: string
    latestPostAt?: string | null
}

export interface TagInfo {
    tagId: number
    tagName: string
    postCount: number
}

export interface TagResponse {
    tags: TagInfo[]
}

export interface PostPoll {
    pollId: number
    question: string
    multipleChoiceEnabled: boolean
    anonymousEnabled: boolean
    closesAt?: string | null
    options: PostPollOption[]
}

export interface PostPollOption {
    optionId: number
    optionText: string
    sortOrder: number
    voteCount: number
    selected: boolean
}

export interface HomeLandingResponse {
    curatedPosts: FeedPost[]
    latestPosts: FeedPost[]
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
