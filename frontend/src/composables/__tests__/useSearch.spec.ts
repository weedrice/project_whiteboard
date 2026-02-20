import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { useSearch } from '../useSearch'
import { searchApi } from '@/api/search'
import { QUERY_STALE_TIME } from '@/utils/constants'

const mocks = vi.hoisted(() => {
    const queryOptions: Array<Record<string, unknown>> = []
    return { queryOptions }
})

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.queryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: async () => {
                if (options.queryFn) {
                    return await (options.queryFn as () => Promise<unknown>)()
                }
                return null
            },
        }
    }),
}))

vi.mock('@/api/search', () => ({
    searchApi: {
        search: vi.fn(),
        searchPosts: vi.fn(),
    },
}))

describe('useSearch', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
    })

    it('fetches search posts and supports q/keyword enabled conditions', async () => {
        vi.mocked(searchApi.searchPosts).mockResolvedValueOnce({
            data: { data: { content: [{ postId: 1 }] } },
        } as never)

        const { useSearchPosts } = useSearch()
        const params = ref({ q: 'vue', page: 0, size: 20 })
        useSearchPosts(params as never)

        const options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['search', 'posts', params])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        expect((options.placeholderData as (prev: unknown) => unknown)('prev')).toBe('prev')

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(searchApi.searchPosts).toHaveBeenCalledWith({ q: 'vue', page: 0, size: 20 })
        expect(result).toEqual({ content: [{ postId: 1 }] })

        const keywordParams = ref({ keyword: 'vite' })
        useSearchPosts(keywordParams as never)
        const keywordOptions = mocks.queryOptions.at(-1)!
        expect((keywordOptions.enabled as ReturnType<typeof computed>).value).toBe(true)

        const disabledParams = ref({})
        useSearchPosts(disabledParams as never)
        const disabledOptions = mocks.queryOptions.at(-1)!
        expect((disabledOptions.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('fetches integrated search and uses q-only enabled condition', async () => {
        vi.mocked(searchApi.search).mockResolvedValueOnce({
            data: { data: { posts: [{ postId: 2 }], boards: [] } },
        } as never)

        const { useIntegratedSearch } = useSearch()
        const params = ref({ q: 'pinia', size: 5 })
        useIntegratedSearch(params as never)

        const options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['search', 'integrated', params])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        expect((options.placeholderData as (prev: unknown) => unknown)('keep')).toBe('keep')

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(searchApi.search).toHaveBeenCalledWith({ q: 'pinia', size: 5 })
        expect(result).toEqual({ posts: [{ postId: 2 }], boards: [] })

        const disabledParams = ref({ keyword: 'only-keyword' })
        useIntegratedSearch(disabledParams as never)
        const disabledOptions = mocks.queryOptions.at(-1)!
        expect((disabledOptions.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('returns mocked popular keywords with medium staleTime', async () => {
        vi.useFakeTimers()

        const { usePopularKeywords } = useSearch()
        usePopularKeywords()
        const options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['search', 'popular'])
        expect(options.staleTime).toBe(QUERY_STALE_TIME.MEDIUM)

        const promise = (options.queryFn as () => Promise<unknown>)()
        await vi.advanceTimersByTimeAsync(500)
        const result = await promise as Array<{ keyword: string; count: number }>

        expect(result).toHaveLength(5)
        expect(result[0]).toEqual({ keyword: 'Vue 3', count: 120 })

        vi.useRealTimers()
    })
})
