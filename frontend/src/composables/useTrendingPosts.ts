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
                // API가 List를 직접 반환하는 경우 PageResponse 형태로 변환
                const responseData = data.data
                if (Array.isArray(responseData)) {
                    // List 응답을 PageResponse로 래핑
                    return {
                        content: responseData,
                        totalElements: responseData.length,
                        totalPages: 1,
                        size: responseData.length,
                        number: pageParam as number,
                        first: true,
                        last: true, // List 응답이면 더 이상 페이지 없음
                        empty: responseData.length === 0
                    } as PageResponse<PostSummary>
                }
                // PageResponse 형태인 경우 그대로 반환
                return responseData as PageResponse<PostSummary>
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

    // Flattened posts (filter out undefined/null values)
    const posts = computed(() => {
        return query.data.value?.pages
            .flatMap(page => page.content)
            .filter((post): post is PostSummary => post != null && post.postId != null) ?? []
    })

    return {
        ...query,
        posts
    }
}
