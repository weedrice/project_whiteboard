import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useBoard } from '../useBoard'
import { ref } from 'vue'
import { boardApi } from '@/api/board'
import { searchApi } from '@/api/search'

// Mock vue-query
const mockInvalidateQueries = vi.fn()
vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries
    })),
    useQuery: vi.fn(({ queryFn }) => {
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn(),
            _queryFn: queryFn
        }
    }),
    useMutation: vi.fn(({ mutationFn, onSuccess }) => {
        return {
            mutate: async (variables: unknown) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
            },
            mutateAsync: async (variables: unknown) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
            },
            isLoading: ref(false),
            error: ref(null)
        }
    })
}))

// Mock APIs
vi.mock('@/api/board', () => ({
    boardApi: {
        getBoards: vi.fn(),
        getBoard: vi.fn(),
        getPosts: vi.fn(),
        getNotices: vi.fn(),
        subscribeBoard: vi.fn(),
        unsubscribeBoard: vi.fn(),
        getCategories: vi.fn()
    }
}))

vi.mock('@/api/search', () => ({
    searchApi: {
        searchPosts: vi.fn()
    }
}))

describe('useBoard', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    describe('useBoards', () => {
        it('returns query hooks for fetching boards', () => {
            const { useBoards } = useBoard()
            const result = useBoards()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
            expect(result).toHaveProperty('error')
        })
    })

    describe('useBoardDetail', () => {
        it('returns query hooks for fetching board detail', () => {
            const { useBoardDetail } = useBoard()
            const boardUrl = ref('free')
            const result = useBoardDetail(boardUrl)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })
    })

    describe('useBoardPosts', () => {
        it('returns query hooks for fetching board posts', () => {
            const { useBoardPosts } = useBoard()
            const boardUrl = ref('free')
            const params = ref({ page: 0, size: 10 })

            const result = useBoardPosts(boardUrl, params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('uses search API when isSearching is true', async () => {
            vi.mocked(searchApi.searchPosts).mockResolvedValue({
                data: { data: { content: [] } }
            } as any)

            const { useBoardPosts } = useBoard()
            const boardUrl = ref('free')
            const params = ref({ page: 0, size: 10, q: 'test' })
            const isSearching = ref(true)

            const result = useBoardPosts(boardUrl, params, isSearching)

            // Execute the queryFn
            if (result._queryFn) {
                await result._queryFn()
            }

            expect(searchApi.searchPosts).toHaveBeenCalledWith({
                page: 0,
                size: 10,
                q: 'test',
                boardUrl: 'free'
            })
        })

        it('uses board API when isSearching is false', async () => {
            vi.mocked(boardApi.getPosts).mockResolvedValue({
                data: { data: { content: [] } }
            } as any)

            const { useBoardPosts } = useBoard()
            const boardUrl = ref('free')
            const params = ref({ page: 0, size: 10 })
            const isSearching = ref(false)

            const result = useBoardPosts(boardUrl, params, isSearching)

            // Execute the queryFn
            if (result._queryFn) {
                await result._queryFn()
            }

            expect(boardApi.getPosts).toHaveBeenCalledWith('free', { page: 0, size: 10 })
        })
    })

    describe('useBoardNotices', () => {
        it('returns query hooks for fetching notices', () => {
            const { useBoardNotices } = useBoard()
            const boardUrl = ref('free')

            const result = useBoardNotices(boardUrl)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })
    })

    describe('useSubscribeBoard', () => {
        it('calls subscribeBoard when not subscribed', async () => {
            vi.mocked(boardApi.subscribeBoard).mockResolvedValue({ data: { success: true } } as any)

            const { useSubscribeBoard } = useBoard()
            const mutation = useSubscribeBoard()

            await mutation.mutateAsync({ boardUrl: 'free', isSubscribed: false })

            expect(boardApi.subscribeBoard).toHaveBeenCalledWith('free')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['board', 'free'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards'] })
        })

        it('calls unsubscribeBoard when already subscribed', async () => {
            vi.mocked(boardApi.unsubscribeBoard).mockResolvedValue({ data: { success: true } } as any)

            const { useSubscribeBoard } = useBoard()
            const mutation = useSubscribeBoard()

            await mutation.mutateAsync({ boardUrl: 'free', isSubscribed: true })

            expect(boardApi.unsubscribeBoard).toHaveBeenCalledWith('free')
        })
    })

    describe('useBoardCategories', () => {
        it('returns query hooks for fetching categories', () => {
            const { useBoardCategories } = useBoard()
            const boardUrl = ref('free')

            const result = useBoardCategories(boardUrl)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })
    })
})

