import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWriteBoardSheet } from '../useWriteBoardSheet'
import { boardApi } from '@/api/board'
import { useToastStore } from '@/stores/toast'
import { apiDataResponse } from '@/test/apiResponseFixtures'
import i18n from '@/i18n'
import { createDeferred } from '@/test/async'

const routerPush = vi.fn()
const fetchQuery = vi.fn()
const cancelQueries = vi.fn()
const authStore = {
    isAuthenticated: true,
    user: { role: 'USER' },
    sessionGeneration: 7,
}
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
    useAuthStore: () => authStore,
}))

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({
        fetchQuery,
        cancelQueries,
    }),
}))

vi.mock('@/features/board/useBoard', () => ({
    boardDetailQueryKey: (boardUrl: string) => ['board', 'detail', boardUrl],
    fetchBoardDetail: async (boardUrl: string, config?: { signal?: AbortSignal }) => {
        const { data } = await boardApi.getBoard(boardUrl, config)
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
        authStore.isAuthenticated = true
        authStore.sessionGeneration = 7
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
        sheet.showWriteSheet.value = true
        await sheet.goToBoardWrite('free')

        expect(routerPush).not.toHaveBeenCalledWith('/board/free/write')
        expect(boardApi.getBoard).toHaveBeenCalledWith('free', {
            signal: expect.any(AbortSignal),
        })
        expect(useToastStore().toasts.at(-1)?.message).toBe(i18n.global.t('common.messages.boardWriteForbidden'))
        sheet.closeWriteSheet()
        await nextTick()
    })

    it('navigates when at least one category is writable', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValue(
            apiDataResponse<typeof boardApi.getBoard>({
                isAdmin: false,
                categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
            })
        )

        const sheet = useWriteBoardSheet()
        sheet.showWriteSheet.value = true
        await sheet.goToBoardWrite('free')

        expect(routerPush).toHaveBeenCalledWith('/board/free/write')
        expect(boardApi.getBoard).toHaveBeenCalledWith('free', {
            signal: expect.any(AbortSignal),
        })
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

    it('keeps only the latest board access result', async () => {
        const responseA = apiDataResponse<typeof boardApi.getBoard>({
            isAdmin: false,
            categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
        })
        const responseB = apiDataResponse<typeof boardApi.getBoard>({
            isAdmin: false,
            categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
        })
        const first = createDeferred<typeof responseA>()
        const second = createDeferred<typeof responseB>()
        vi.mocked(boardApi.getBoard).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
        const sheet = useWriteBoardSheet()
        sheet.showWriteSheet.value = true

        const requestA = sheet.goToBoardWrite('board-a')
        const requestB = sheet.goToBoardWrite('board-b')
        const firstSignal = vi.mocked(boardApi.getBoard).mock.calls[0][1]?.signal
        second.resolve(responseB)
        await requestB
        first.resolve(responseA)
        await requestA

        expect(routerPush).toHaveBeenCalledTimes(1)
        expect(routerPush).toHaveBeenCalledWith('/board/board-b/write')
        expect(firstSignal?.aborted).toBe(true)
        expect(cancelQueries).not.toHaveBeenCalled()
    })

    it('does not navigate or toast after the sheet closes or the session changes', async () => {
        const response = apiDataResponse<typeof boardApi.getBoard>({
            isAdmin: false,
            categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }],
        })
        const pending = createDeferred<typeof response>()
        vi.mocked(boardApi.getBoard).mockReturnValueOnce(pending.promise)
        const sheet = useWriteBoardSheet()
        sheet.showWriteSheet.value = true

        const request = sheet.goToBoardWrite('free')
        authStore.sessionGeneration = 8
        sheet.closeWriteSheet()
        pending.resolve(response)
        await request

        expect(routerPush).not.toHaveBeenCalled()
        expect(useToastStore().toasts).toHaveLength(0)
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
