import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useBoard } from '../useBoard'
import { ref } from 'vue'

// Mock vue-query
vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        invalidateQueries: vi.fn()
    })),
    useQuery: vi.fn(({ queryFn }) => {
        const data = queryFn()
        return { data: ref(data) }
    }),
    useMutation: vi.fn(({ mutationFn, onSuccess }) => {
        return {
            mutate: async (variables: any) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
            }
        }
    })
}))

// Mock APIs
vi.mock('@/api/board', () => ({
    boardApi: {
        getBoards: vi.fn().mockResolvedValue({ data: { data: [{ boardUrl: 'free', boardName: 'Free Board' }] } }),
        getBoard: vi.fn().mockResolvedValue({ data: { data: { boardUrl: 'free', boardName: 'Free Board' } } }),
        getPosts: vi.fn().mockResolvedValue({ data: { data: { content: [] } } }),
        getNotices: vi.fn().mockResolvedValue({ data: { data: [] } }),
        subscribeBoard: vi.fn().mockResolvedValue({ success: true }),
        unsubscribeBoard: vi.fn().mockResolvedValue({ success: true }),
        getCategories: vi.fn().mockResolvedValue({ data: { data: [] } })
    }
}))

vi.mock('@/api/search', () => ({
    searchApi: {
        searchPosts: vi.fn().mockResolvedValue({ data: { data: { content: [] } } })
    }
}))

describe('useBoard', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('fetches boards', async () => {
        const { useBoards } = useBoard()
        const { data } = useBoards()
        expect(data).toBeDefined()
    })

    it('fetches board detail', async () => {
        const { useBoardDetail } = useBoard()
        const boardUrl = ref('free')
        const { data } = useBoardDetail(boardUrl)
        expect(data).toBeDefined()
    })

    it('subscribes to a board', async () => {
        const { useSubscribeBoard } = useBoard()
        const mutation = useSubscribeBoard()
        await mutation.mutate({ boardUrl: 'free', isSubscribed: false })
        // Expect no error
    })
})
