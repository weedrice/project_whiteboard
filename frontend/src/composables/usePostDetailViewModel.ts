import { computed, type Ref } from 'vue'
import type { Post } from '@/types'

type PostWithBackendBooleans = Post & {
  isLiked?: boolean
  isScrapped?: boolean
}

export interface PostDetailViewModel {
  postId: number
  title: string
  createdAt: string
  viewCount: number
  commentCount: number
  likeCount: number
  liked: boolean
  scrapped: boolean
  tags: string[]
  boardName: string
  boardUrl: string
  authorUserId: number
  authorDisplayName: string
}

export function toPostDetailViewModel(post: Post): PostDetailViewModel {
  const normalizedPost = post as PostWithBackendBooleans

  return {
    postId: post.postId,
    title: post.title,
    createdAt: post.createdAt,
    viewCount: post.viewCount,
    commentCount: post.commentCount,
    likeCount: post.likeCount,
    liked: normalizedPost.liked ?? normalizedPost.isLiked ?? false,
    scrapped: normalizedPost.scrapped ?? normalizedPost.isScrapped ?? false,
    tags: post.tags ?? [],
    boardName: post.board.boardName,
    boardUrl: post.board.boardUrl,
    authorUserId: post.author.userId,
    authorDisplayName: post.author.displayName
  }
}

export function usePostDetailViewModel(post: Ref<Post | undefined>) {
  return computed(() => (post.value ? toPostDetailViewModel(post.value) : null))
}
