import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { postApi } from '@/api/post'
import type { PostSummary, PageResponse } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'

/**
 * Trending Posts를 Infinite Scroll로 가져오는 Composable
 */
export function useTrendingPosts(size: number = 10) {
    const query = useInfiniteQuery<PageResponse<PostSummary>>({
        queryKey: ['posts', 'trending'],
        queryFn: async ({ pageParam = 0 }) => {
            const { data } = await postApi.getTrendingPosts(pageParam as number, size)
            if (data.success) {
                return data.data
            }
            return {
                content: [],
                totalElements: 0,
                totalPages: 0,
                size: 0,
                number: 0,
                first: true,
                last: true,
                empty: true
            } as PageResponse<PostSummary>
        },
        initialPageParam: 0,
        getNextPageParam: (lastPage) => {
            if (lastPage.last) return undefined
            return lastPage.number + 1
        },
        staleTime: QUERY_STALE_TIME.SHORT,
        placeholderData: (previousData) => previousData
    })

    // Flattened posts
    const posts = computed(() => {
        return query.data.value?.pages.flatMap(page => page.content) ?? []
    })

    return {
        ...query,
        posts
    }
}
