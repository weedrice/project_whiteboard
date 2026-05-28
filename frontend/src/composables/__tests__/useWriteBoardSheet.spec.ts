import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWriteBoardSheet } from '../useWriteBoardSheet'
import { boardApi } from '@/api/board'
import { useToastStore } from '@/stores/toast'

const routerPush = vi.fn()
const fetchQuery = vi.fn()
const route = {
    fullPath: '/board/free',
    name: 'home',
}

vi.mock('vue-router', () => ({
    useRoute: () => route,
    useRouter: () => ({
        push: routerPush,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({
        isAuthenticated: true,
        user: { role: 'USER' },
    }),
}))

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({
        fetchQuery,
    }),
}))

vi.mock('@/composables/useBoard', () => ({
    boardDetailQueryKey: (boardUrl: string) => ['board', boardUrl],
    fetchBoardDetail: async (boardUrl: string) => {
        const { data } = await boardApi.getBoard(boardUrl)
        return data.data
    },
    createBoardDetailQueryOptions: (boardUrl: string) => ({
        queryKey: ['board', boardUrl],
        queryFn: async () => {
            const { data } = await boardApi.getBoard(boardUrl)
            return data.data
        },
        staleTime: 60000,
    }),
    useBoard: () => ({
        useBoards: () => ({
            data: ref([{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10, postCount: 12 }]),
            isError: ref(false),
        }),
        useSubscribedBoards: () => ({
            data: ref([{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10, postCount: 12 }]),
            isLoading: ref(false),
            isError: ref(false),
        }),
    }),
}))

vi.mock('@/api/board', () => ({
    boardApi: {
        getBoard: vi.fn(),
    },
}))

describe('useWriteBoardSheet', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
        fetchQuery.mockImplementation(async ({ queryFn }: { queryFn: () => Promise<unknown> }) => queryFn())
    })

    it('blocks navigation when the user cannot write to any category', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue({
            data: {
                data: {
                    isAdmin: false,
                    categories: [{ categoryId: 1, name: 'Admin', minWriteRole: 'BOARD_ADMIN' }],
                },
            },
        } as never)

        const sheet = useWriteBoardSheet()
        await sheet.goToBoardWrite('free')

        expect(routerPush).not.toHaveBeenCalledWith('/board/free/write')
        expect(fetchQuery).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['board', 'free'] }))
        expect(useToastStore().toasts.at(-1)?.message).toBe('You do not have permission to write on this board.')
    })

    it('navigates when at least one category is writable', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue({
            data: {
                data: {
                    isAdmin: false,
                    categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
                },
            },
        } as never)

        const sheet = useWriteBoardSheet()
        await sheet.goToBoardWrite('free')

        expect(routerPush).toHaveBeenCalledWith('/board/free/write')
        expect(boardApi.getBoard).toHaveBeenCalledWith('free')
    })
})
