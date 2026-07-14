import type { PostSummary } from './board'
import type { PageResponse } from './common'

export interface PersonalFeedItem {
  feedId: number
  feedType: string
  contentType: string
  contentId: number
  isRead: boolean
  createdAt: string
  post: PostSummary | null
}

export type PersonalFeedPage = PageResponse<PersonalFeedItem>
