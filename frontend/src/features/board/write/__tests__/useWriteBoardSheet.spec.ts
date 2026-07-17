import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWriteBoardSheet } from '../useWriteBoardSheet'
import { boardApi } from '@/api/board'
import { useToastStore } from '@/stores/toast'
import { apiDataResponse } from '@/test/apiResponseFixtures'
import i18n from '@/i18n'

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

vi.mock('@/features/board/useBoard', () => ({
    boardDetailQueryKey: (boardUrl: string) => ['board', 'detail', boardUrl],
    fetchBoardDetail: async (boardUrl: string) => {
        const { data } = await boardApi.getBoard(boardUrl)
        return data.data
    },
    createBoardDetailQueryOptions: (boardUrl: string) => ({
        queryKey: ['board', 'detail', boardUrl],
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
            refetch: vi.fn(),
        }),
        useSubscribedBoards: () => ({
            data: ref([{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10, postCount: 12 }]),
            isLoading: ref(false),
            isError: ref(false),
            refetch: vi.fn(),
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
        document.body.style.overflow = ''
    })

    it('blocks navigation when the user cannot write to any category', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue(
            apiDataResponse<typeof boardApi.getBoard>({
                isAdmin: false,
                categories: [{ categoryId: 1, name: 'Admin', minWriteRole: 'BOARD_ADMIN' }],
            })
        )

        const sheet = useWriteBoardSheet()
        await sheet.goToBoardWrite('free')

        expect(routerPush).not.toHaveBeenCalledWith('/board/free/write')
        expect(fetchQuery).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['board', 'detail', 'free'] }))
        expect(useToastStore().toasts.at(-1)?.message).toBe(i18n.global.t('common.messages.boardWriteForbidden'))
    })

    it('navigates when at least one category is writable', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue(
            apiDataResponse<typeof boardApi.getBoard>({
                isAdmin: false,
                categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
            })
        )

        const sheet = useWriteBoardSheet()
        await sheet.goToBoardWrite('free')

        expect(routerPush).toHaveBeenCalledWith('/board/free/write')
        expect(boardApi.getBoard).toHaveBeenCalledWith('free')
    })

    it('locks background scrolling only while the write sheet is open', async () => {
        document.body.style.overflow = 'auto'
        const sheet = useWriteBoardSheet()

        sheet.showWriteSheet.value = true
        await nextTick()
        expect(document.body.style.overflow).toBe('hidden')

        sheet.closeWriteSheet()
        await nextTick()
        expect(document.body.style.overflow).toBe('auto')
    })

    it('traps Tab inside the opened sheet, closes on Escape, and restores trigger focus', async () => {
        const trigger = document.createElement('button')
        const sheetElement = document.createElement('div')
        sheetElement.tabIndex = -1
        const first = document.createElement('button')
        const last = document.createElement('button')
        sheetElement.append(first, last)
        document.body.append(trigger, sheetElement)
        trigger.focus()

        const sheet = useWriteBoardSheet()
        sheet.fabButtonRef.value = trigger
        sheet.sheetRef.value = sheetElement
        await sheet.openWriteSheet()
        await nextTick()

        expect(sheet.showWriteSheet.value).toBe(true)
        expect(document.activeElement).toBe(sheetElement)

        const initialShiftTab = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true })
        sheet.handleSheetKeydown(initialShiftTab)
        expect(initialShiftTab.defaultPrevented).toBe(true)
        expect(document.activeElement).toBe(last)

        last.focus()
        const tab = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true })
        sheet.handleSheetKeydown(tab)
        expect(tab.defaultPrevented).toBe(true)
        expect(document.activeElement).toBe(first)

        const escape = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })
        sheet.handleSheetKeydown(escape)
        await nextTick()
        expect(sheet.showWriteSheet.value).toBe(false)
        expect(document.activeElement).toBe(trigger)

        trigger.remove()
        sheetElement.remove()
    })
})
