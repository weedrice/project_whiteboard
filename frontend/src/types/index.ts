import type { BoardSearchItem, PostSummary } from './board'
import type { CommentResponse } from './comment'
import type { UserSummary } from './user'

export * from './common'
export * from './user'
export * from './auth'
export * from './board'
export * from './comment'
export * from './config'
export * from './message'
export * from './notification'
export * from './report'
export * from './admin'
export * from './search'
export * from './feed'
export * from '../api/file'

export interface IntegratedSearchResultGroup<T> {
    items: T[]
    totalElements: number
    totalPages: number
    page: number
    size: number
    hasMore: boolean
}

export interface IntegratedSearchResponse {
    postResults: IntegratedSearchResultGroup<PostSummary>
    commentResults: IntegratedSearchResultGroup<CommentResponse>
    userResults: IntegratedSearchResultGroup<UserSummary>
    boardResults: BoardSearchItem[]
    boardResultPage?: IntegratedSearchResultGroup<BoardSearchItem>
    keyword: string
}
