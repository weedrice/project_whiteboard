import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { postApi, type BackendPageResponse } from '@/api/post'
import type { PostSummary, PageResponse } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'

function normalizeTrendingPage(raw: BackendPageResponse<PostSummary>, fallbackPage: number): PageResponse<PostSummary> {
    const content = raw.content ?? []
    const number = typeof raw.number === 'number'
        ? raw.number
        : (typeof raw.page === 'number' ? raw.page : fallbackPage)
    const totalElements = typeof raw.totalElements === 'number' ? raw.totalElements : content.length
    const totalPages = typeof raw.totalPages === 'number' ? raw.totalPages : 1
    const first = typeof raw.first === 'boolean' ? raw.first : number <= 0
    const last = typeof raw.last === 'boolean'
        ? raw.last
        : (typeof raw.hasNext === 'boolean' ? !raw.hasNext : totalPages <= 1 || number >= totalPages - 1)

    return {
        content,
        totalElements,
        totalPages,
        size: typeof raw.size === 'number' ? raw.size : content.length,
        number,
        first,
        last,
        empty: typeof raw.empty === 'boolean' ? raw.empty : content.length === 0,
    }
}

/**
 * Trending Posts를 Infinite Scroll로 가져오는 Composable
 */
export function useTrendingPosts(size: number = 10) {
    const query = useInfiniteQuery<PageResponse<PostSummary>>({
        queryKey: ['posts', 'trending'],
        queryFn: async ({ pageParam = 0 }) => {
            const { data } = await postApi.getTrendingPosts(pageParam as number, size)
            if (data.success) {
                return normalizeTrendingPage(data.data as BackendPageResponse<PostSummary>, pageParam as number)
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
