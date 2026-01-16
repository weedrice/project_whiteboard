import type { PageResponse } from './common'
import type { PostSummary } from './board'
import type { CommentResponse } from './comment'
import type { UserSummary } from './user'
import type { Board } from './board'

export * from './common'
export * from './user'
export * from './auth'
export * from './board'
export * from './comment'
export * from './message'
export * from './notification'
export * from './report'
export * from './admin'
export * from './search'
export * from './tag'
export * from '../api/file'

export interface IntegratedSearchResponse {
    posts: PageResponse<PostSummary>
    comments: PageResponse<CommentResponse>
    users: PageResponse<UserSummary>
    boards: Board[]
    keyword: string
}
