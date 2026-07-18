import type { QueryClient } from '@tanstack/vue-query'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { feedQueryKeys } from '@/features/feed/feedQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

export const messageVisibilityQueryKeys = {
  all: ['messages'] as const,
  unreadCount: ['messages', 'unread-count'] as const,
}

export function invalidateBlockVisibilityCaches(
  queryClient: QueryClient,
  sessionGeneration: number,
  targetUserId: string | number,
) {
  const visibilityRoots: readonly (readonly unknown[])[] = [
    userQueryKeys.blocksRoot,
    userQueryKeys.profile(targetUserId),
    userQueryKeys.publicPostsRoot(targetUserId),
    userQueryKeys.publicCommentsRoot(targetUserId),
    postQueryKeys.detailsRoot,
    postQueryKeys.lists,
    postQueryKeys.boardPostsRoot,
    homeQueryKeys.all,
    searchQueryKeys.all,
    feedQueryKeys.all,
    commentQueryKeys.all,
    messageVisibilityQueryKeys.all,
    messageVisibilityQueryKeys.unreadCount,
  ]

  return Promise.all(visibilityRoots.map((queryKey) => queryClient.invalidateQueries({
    queryKey: sessionQueryKey(sessionGeneration, queryKey),
  })))
}
