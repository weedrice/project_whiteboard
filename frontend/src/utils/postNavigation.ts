import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import type { PostSummary } from '@/types'

type PostRouteSource = Pick<PostSummary, 'postId'> & {
  boardUrl?: string | number
}

type InquiryPostSource = {
  boardUrl?: string | number
}

export function resolvePostDetailRoute(
  post: PostRouteSource,
  boardUrl: string,
  linkQuery?: LocationQueryRaw
): RouteLocationRaw {
  return {
    name: 'post-detail',
    params: {
      boardUrl,
      postId: post.postId
    },
    query: linkQuery
  }
}

export function isInquiryPostItem(post: InquiryPostSource, fallbackBoardUrl?: string | number): boolean {
  return String(post.boardUrl || fallbackBoardUrl || '').trim().toLowerCase() === 'inquiry'
}
