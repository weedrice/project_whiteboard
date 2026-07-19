import type { QueryClient } from '@tanstack/vue-query'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { feedQueryKeys } from '@/features/feed/feedQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

export function invalidateProfileAuthorCaches(
  queryClient: QueryClient,
  sessionGeneration: number,
  userId?: string | number | null,
) {
  const queryKeys: readonly (readonly unknown[])[] = [
    userQueryKeys.me,
    ...(userId == null ? [] : [
      userQueryKeys.profile(userId),
      userQueryKeys.publicPostsRoot(userId),
      userQueryKeys.publicCommentsRoot(userId),
    ]),
    userQueryKeys.scrapsRoot,
    userQueryKeys.recentlyViewedPostsRoot,
    postQueryKeys.detailsRoot,
    postQueryKeys.lists,
    postQueryKeys.boardPostsRoot,
    ['board', 'notices'],
    commentQueryKeys.all,
    homeQueryKeys.all,
    searchQueryKeys.all,
    feedQueryKeys.all,
  ]

  return Promise.all(queryKeys.map((queryKey) => queryClient.invalidateQueries({
    queryKey: sessionQueryKey(sessionGeneration, queryKey),
  })))
}
