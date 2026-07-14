import { computed, type Ref } from 'vue'
import { useInfiniteQuery } from '@tanstack/vue-query'
import { feedApi } from '@/api/feed'
import { unwrapAxiosApiData } from '@/api/response'
import { toFeedPosts } from '@/utils/postViewModel'
import { optionalQuerySignal } from '@/utils/querySignal'
import { feedQueryKeys } from './feedQueryKeys'

const PERSONAL_FEED_PAGE_SIZE = 20

export function usePersonalFeed(
  userId: Ref<string | number | null | undefined>,
  enabled: Ref<boolean>,
) {
  const query = useInfiniteQuery({
    queryKey: computed(() => feedQueryKeys.personal(userId.value ?? 'anonymous')),
    initialPageParam: 0,
    queryFn: async ({ pageParam, signal }) => unwrapAxiosApiData(
      await feedApi.getMyFeeds(
        Number(pageParam),
        PERSONAL_FEED_PAGE_SIZE,
        optionalQuerySignal(undefined, { signal }),
      ),
    ),
    getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
    enabled: computed(() => enabled.value && userId.value != null),
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
