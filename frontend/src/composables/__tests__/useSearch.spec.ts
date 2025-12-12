import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useSearch } from '../useSearch'
import { searchApi } from '@/api/search'

// Store queryFn to test it
let capturedQueryFn: (() => Promise<unknown>) | null = null

// Mock vue-query
const mockUseQuery = vi.fn((options) => {
    capturedQueryFn = options.queryFn
    return {
        data: ref(null),
        isLoading: ref(false),
        error: ref(null),
        refetch: vi.fn(),
        _queryKey: options.queryKey
    }
})

vi.mock('@tanstack/vue-query', () => ({
    useQuery: (options: unknown) => mockUseQuery(options),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: vi.fn()
    }))
}))

vi.mock('@/api/search', () => ({
    searchApi: {
        searchPosts: vi.fn()
    }
}))

describe('useSearch', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        capturedQueryFn = null
    })

    describe('useSearchPosts', () => {
        it('returns query hooks', () => {
            const { useSearchPosts } = useSearch()
            const params = ref({ q: 'test' })

            const result = useSearchPosts(params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
            expect(result).toHaveProperty('error')
        })

        it('is called with search query params', () => {
            const { useSearchPosts } = useSearch()
            const params = ref({ q: 'vue' })

            useSearchPosts(params)

            expect(mockUseQuery).toHaveBeenCalled()
        })

        it('queryFn calls searchApi.searchPosts with params', async () => {
            vi.mocked(searchApi.searchPosts).mockResolvedValue({
                data: { data: { content: [{ id: 1, title: 'Test' }] } }
            } as any)

            const { useSearchPosts } = useSearch()
            const params = ref({ q: 'test', page: 0, size: 10 })

            useSearchPosts(params)

            expect(capturedQueryFn).toBeDefined()
            const result = await capturedQueryFn!()

            expect(searchApi.searchPosts).toHaveBeenCalledWith({ q: 'test', page: 0, size: 10 })
            expect(result).toEqual({ content: [{ id: 1, title: 'Test' }] })
        })
    })

    describe('usePopularKeywords', () => {
        it('returns query hooks', () => {
            const { usePopularKeywords } = useSearch()

            const result = usePopularKeywords()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
            expect(result).toHaveProperty('error')
        })

        it('is called for popular keywords', () => {
            vi.clearAllMocks()
            const { usePopularKeywords } = useSearch()

            usePopularKeywords()

            expect(mockUseQuery).toHaveBeenCalled()
        })

        it('queryFn returns mock popular keywords', async () => {
            vi.useFakeTimers()

            const { usePopularKeywords } = useSearch()

            usePopularKeywords()

            expect(capturedQueryFn).toBeDefined()

            // Start the async operation
            const resultPromise = capturedQueryFn!()

            // Fast-forward past the 500ms delay
            await vi.advanceTimersByTimeAsync(500)

            const result = await resultPromise as { keyword: string, count: number }[]

            expect(result).toHaveLength(5)
            expect(result[0]).toHaveProperty('keyword')
            expect(result[0]).toHaveProperty('count')

            vi.useRealTimers()
        })
    })
})
