import { computed, type Ref } from 'vue'
import type { BadgeCompact, Post } from '@/types'

export interface PostDetailViewModel {
  postId: number
  title: string
  createdAt: string
  editCount: number
  viewCount: number
  commentCount: number
  likeCount: number
  liked: boolean
  scrapped: boolean
  lastReadCommentId?: number | null
  lastViewedAt?: string | null
  tags: string[]
  boardName: string
  boardUrl: string
  authorUserId: number
  authorDisplayName: string
  representativeBadge?: BadgeCompact | null
  isBlinded: boolean
  blindReason?: string | null
  isBoardAdmin: boolean
  poll?: Post['poll']
  seriesNavigation?: Post['seriesNavigation']
}

export function toPostDetailViewModel(post: Post): PostDetailViewModel {
  return {
    postId: post.postId,
    title: post.title,
    createdAt: post.createdAt,
    editCount: post.editCount ?? 0,
    viewCount: post.viewCount,
    commentCount: post.commentCount,
    likeCount: post.likeCount,
    liked: post.liked ?? false,
    scrapped: post.scrapped ?? false,
    lastReadCommentId: post.lastReadCommentId ?? null,
    lastViewedAt: post.lastViewedAt ?? null,
    tags: post.tags ?? [],
    boardName: post.board.boardName,
    boardUrl: post.board.boardUrl,
    authorUserId: post.author.userId,
    authorDisplayName: post.author.displayName,
    representativeBadge: post.author.representativeBadge ?? null,
    isBlinded: post.isBlinded ?? false,
    blindReason: post.blindReason ?? null,
    isBoardAdmin: post.board.isAdmin ?? false,
    poll: post.poll ?? null,
    seriesNavigation: post.seriesNavigation ?? null,
  }
}

export function usePostDetailViewModel(post: Ref<Post | undefined>) {
  return computed(() => (post.value ? toPostDetailViewModel(post.value) : null))
}
