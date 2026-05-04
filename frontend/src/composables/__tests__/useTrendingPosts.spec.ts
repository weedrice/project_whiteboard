import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTrendingPosts } from '../useTrendingPosts'

const mocks = vi.hoisted(() => {
    const postApi = {
        getTrendingPosts: vi.fn(),
    }
    const queryOptions: Array<Record<string, unknown>> = []
    const useInfiniteQuery = vi.fn((options: Record<string, unknown>) => {
        queryOptions.push(options)
        return {
            data: ref({ pages: [] }),
        }
    })

    return {
        postApi,
        queryOptions,
        useInfiniteQuery,
    }
})

vi.mock('@/api/post', () => ({
    postApi: mocks.postApi,
}))

vi.mock('@tanstack/vue-query', () => ({
    useInfiniteQuery: mocks.useInfiniteQuery,
}))

describe('useTrendingPosts', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
    })

    it('normalizes backend page and hasNext fields for infinite query paging', async () => {
        mocks.postApi.getTrendingPosts.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    content: [{ postId: 10, title: 'Trending' }],
                    page: 1,
                    size: 10,
                    totalElements: 12,
                    totalPages: 2,
                    hasNext: false,
                    hasPrevious: true,
                },
            },
        })

        useTrendingPosts(10)

        const options = mocks.queryOptions[0]
        const result = await (options.queryFn as (context: { pageParam: number }) => Promise<unknown>)({ pageParam: 1 })

        expect(mocks.postApi.getTrendingPosts).toHaveBeenCalledWith(1, 10)
        expect(result).toMatchObject({
            content: [{ postId: 10, title: 'Trending' }],
            number: 1,
            first: false,
            last: true,
            empty: false,
        })
        expect((options.getNextPageParam as (lastPage: { last: boolean; number: number }) => number | undefined)(
            { last: false, number: 1 },
        )).toBe(2)
    })
})
