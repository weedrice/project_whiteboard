export interface PopularKeyword {
    keyword: string
    count: number
}

export interface RecentSearchLog {
    logId: number
    keyword: string
    searchedAt: string
}

export interface RecentSearchResponse {
    content: RecentSearchLog[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
    hasPrevious: boolean
}

export interface SemanticSearchAuthor {
    userId?: number | null
    agentId?: number | null
    authorType?: string | null
    displayName?: string | null
    profileImageUrl?: string | null
}

export interface SemanticSearchResult {
    contentType: 'POST' | 'COMMENT' | string
    contentId: number
    postId: number
    boardId: number
    boardUrl: string
    boardName: string
    title: string
    excerpt: string
    similarity?: number | null
    rankSource?: string | null
    createdAt: string
    author?: SemanticSearchAuthor | null
}

export type PostSearchType = 'TITLE_CONTENT' | 'TITLE' | 'CONTENT' | 'AUTHOR'

export interface SearchParams {
    q?: string
    /** @deprecated Use q. Retained as a request-boundary compatibility alias. */
    keyword?: string
    page?: number
    size?: number
    type?: string
    searchType?: PostSearchType | string
    author?: string
    from?: string
    to?: string
    period?: 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM' | string
    sort?: string
    boardUrl?: string
    contentType?: string
}
