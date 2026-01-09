import { useInfiniteQuery, type UseInfiniteQueryOptions } from '@tanstack/vue-query'
import { ref, computed, watch } from 'vue'
import { useIntersectionObserver } from './useIntersectionObserver'

/**
 * Infinite Scroll을 위한 쿼리 옵션
 */
export interface InfiniteScrollQueryOptions<TData, TError = unknown> {
    /**
     * 쿼리 키
     */
    queryKey: unknown[]
    
    /**
     * 쿼리 함수
     * @param pageParam 페이지 파라미터
     * @param page 페이지 번호
     */
    queryFn: (pageParam: unknown, page: number) => Promise<{ data: TData[], hasMore: boolean }>
    
    /**
     * 초기 페이지 파라미터
     */
    initialPageParam?: unknown
    
    /**
     * 다음 페이지 파라미터를 계산하는 함수
     */
    getNextPageParam?: (lastPage: { data: TData[], hasMore: boolean }, allPages: { data: TData[], hasMore: boolean }[]) => unknown
    
    /**
     * 추가 쿼리 옵션
     */
    queryOptions?: Omit<UseInfiniteQueryOptions<{ data: TData[], hasMore: boolean }, TError>, 'queryKey' | 'queryFn' | 'initialPageParam' | 'getNextPageParam'>
}

/**
 * Infinite Scroll을 위한 Composable
 * 
 * @example
 * ```typescript
 * const {
 *   data,
 *   isLoading,
 *   isFetchingNextPage,
 *   hasNextPage,
 *   fetchNextPage,
 *   sentinelRef
 * } = useInfiniteScroll({
 *   queryKey: ['posts', 'trending'],
 *   queryFn: async (pageParam, page) => {
 *     const { data } = await postApi.getTrendingPosts(page, 10)
 *     return {
 *       data: data.data.content,
 *       hasMore: !data.data.last
 *     }
 *   },
 *   initialPageParam: 0
 * })
 * ```
 */
export function useInfiniteScroll<TData = unknown, TError = unknown>(
    options: InfiniteScrollQueryOptions<TData, TError>
) {
    const {
        queryKey,
        queryFn,
        initialPageParam = 0,
        getNextPageParam = (lastPage) => lastPage.hasMore ? undefined : undefined,
        queryOptions = {}
    } = options

    // Infinite Query
    const query = useInfiniteQuery({
        queryKey,
        queryFn: ({ pageParam }) => queryFn(pageParam, pageParam as number),
        initialPageParam,
        getNextPageParam: (lastPage, allPages) => {
            if (!lastPage.hasMore) return undefined
            return (allPages.length) as unknown
        },
        ...queryOptions
    })

    // Sentinel element ref
    const sentinelRef = ref<HTMLElement | null>(null)

    // Intersection Observer
    const { isIntersecting } = useIntersectionObserver(sentinelRef, {
        rootMargin: '200px',
        threshold: 0.1
    })

    // 자동으로 다음 페이지 로드
    watch([isIntersecting, () => query.hasNextPage.value, () => query.isFetchingNextPage.value], 
        ([intersecting, hasNext, isFetching]) => {
            if (intersecting && hasNext && !isFetching) {
                query.fetchNextPage()
            }
        }
    )

    // Flattened data
    const data = computed(() => {
        return query.data.value?.pages.flatMap(page => page.data) ?? []
    })

    return {
        ...query,
        data,
        sentinelRef
    }
}
