import { createReactiveAuthScopedQueryOptions } from '@/utils/authScopedQuery'
import { computed, type Ref } from 'vue'
import { useInfiniteQuery } from '@tanstack/vue-query'
import { feedApi } from '@/api/feed'
import { unwrapAxiosApiData } from '@/api/response'
import { toFeedPosts } from '@/utils/postViewModel'
import { feedQueryKeys } from './feedQueryKeys'
import { useAuthStore } from '@/stores/auth'
import { AUTH_SCOPED_QUERY_META, currentSessionQueryKey } from '@/queryAuthScope'

const PERSONAL_FEED_PAGE_SIZE = 20

export function usePersonalFeed(
  userId: Ref<string | number | null | undefined>,
  enabled: Ref<boolean>,
) {
  const authStore = useAuthStore()
  const query = useInfiniteQuery({
    ...createReactiveAuthScopedQueryOptions(authStore, computed(() => currentSessionQueryKey(
      authStore,
      feedQueryKeys.personal(userId.value ?? 'anonymous'),
    )), async ({ pageParam, requestConfig }) => unwrapAxiosApiData(
      await feedApi.getMyFeeds(
        Number(pageParam),
        PERSONAL_FEED_PAGE_SIZE,
        requestConfig,
      ),
    )),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
    enabled: computed(() => enabled.value && userId.value != null),
    meta: AUTH_SCOPED_QUERY_META,
  })

  const posts = computed(() => toFeedPosts(
    query.data.value?.pages.flatMap((page) => (
      page.content
        .filter((item) => item.contentType === 'POST' && item.post != null)
        .map((item) => item.post!)
    )) ?? [],
  ))

  return {
    ...query,
    posts,
  }
}
