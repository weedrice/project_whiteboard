import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWriteBoardSheet } from '../useWriteBoardSheet'
import { boardApi } from '@/api/board'
import { useToastStore } from '@/stores/toast'

const routerPush = vi.fn()
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

vi.mock('@/composables/useBoard', () => ({
    useBoard: () => ({
        useBoards: () => ({
            data: ref([{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10 }]),
            isError: ref(false),
        }),
        useSubscribedBoards: () => ({
            data: ref([{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10 }]),
            isLoading: ref(false),
            isError: ref(false),
        }),
    }),
}))

vi.mock('@/api/board', () => ({
    boardApi: {
        getBoard: vi.fn(),
        getCategories: vi.fn(),
    },
}))

describe('useWriteBoardSheet', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
    })

    it('blocks navigation when the user cannot write to any category', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue({
            data: {
                data: { isAdmin: false },
            },
        } as never)
        vi.mocked(boardApi.getCategories).mockResolvedValue({
            data: {
                data: [{ categoryId: 1, name: 'Admin', minWriteRole: 'BOARD_ADMIN' }],
            },
        } as never)

        const sheet = useWriteBoardSheet()
        await sheet.goToBoardWrite('free')

        expect(routerPush).not.toHaveBeenCalledWith('/board/free/write')
        expect(useToastStore().toasts.at(-1)?.message).toBe('You do not have permission to write on this board.')
    })

    it('navigates when at least one category is writable', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue({
            data: {
                data: { isAdmin: false },
            },
        } as never)
        vi.mocked(boardApi.getCategories).mockResolvedValue({
            data: {
                data: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
            },
        } as never)

        const sheet = useWriteBoardSheet()
        await sheet.goToBoardWrite('free')

        expect(routerPush).toHaveBeenCalledWith('/board/free/write')
    })
})
