import type { PostSummary } from './board'

export interface PersonalFeedItem {
  feedId: number
  feedType: string
  contentType: string
  contentId: number
  isRead?: boolean
  createdAt: string
  post: PostSummary | null
}

export interface PersonalFeedPage {
  content: PersonalFeedItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}
