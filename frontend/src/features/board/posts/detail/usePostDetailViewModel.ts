import { computed, type Ref } from 'vue'
import type { BadgeCompact, Post } from '@/types'
import { normalizePostReactionFlags } from '@/utils/postViewModel'

export interface PostDetailViewModel {
  postId: number
  title: string
  createdAt: string
  viewCount: number
  commentCount: number
  likeCount: number
  liked: boolean
  scrapped: boolean
  lastViewedAt?: string | null
  tags: string[]
  boardName: string
  boardUrl: string
  authorUserId: number
  authorDisplayName: string
  representativeBadge?: BadgeCompact | null
  isBlinded: boolean
  blindReason?: string | null
  pinnedAt?: string | null
  isBoardAdmin: boolean
  poll?: Post['poll']
  seriesNavigation?: Post['seriesNavigation']
}

export function toPostDetailViewModel(post: Post): PostDetailViewModel {
  const normalizedPost = normalizePostReactionFlags(post)

  return {
    postId: post.postId,
    title: post.title,
    createdAt: post.createdAt,
    viewCount: post.viewCount,
    commentCount: post.commentCount,
    likeCount: post.likeCount,
    liked: normalizedPost.liked ?? false,
    scrapped: normalizedPost.scrapped ?? false,
    lastViewedAt: post.lastViewedAt ?? null,
    tags: post.tags ?? [],
    boardName: post.board.boardName,
    boardUrl: post.board.boardUrl,
    authorUserId: post.author.userId,
    authorDisplayName: post.author.displayName,
    representativeBadge: post.author.representativeBadge ?? null,
    isBlinded: post.isBlinded ?? false,
    blindReason: post.blindReason ?? null,
    pinnedAt: post.pinnedAt ?? null,
    isBoardAdmin: post.board.isAdmin ?? false,
    poll: post.poll ?? null,
    seriesNavigation: post.seriesNavigation ?? null,
  }
}

export function usePostDetailViewModel(post: Ref<Post | undefined>) {
  return computed(() => (post.value ? toPostDetailViewModel(post.value) : null))
}
