import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardCategoriesManager } from '../useBoardCategoriesManager'
import { boardApi } from '@/api/board'
import { apiEmptySuccess, apiSuccess, axiosApiResponse } from '@/test/factories'
import { createDeferred } from '@/test/async'
import type { Category } from '@/types'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    confirm: vi.fn(),
    invalidateQueries: vi.fn(),
    loggerError: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('@/api/board', () => ({
    boardApi: {
        getCategories: vi.fn(),
        createCategory: vi.fn(),
        updateCategory: vi.fn(),
        reorderCategories: vi.fn(),
        deleteCategory: vi.fn(),
    },
}))

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({
        invalidateQueries: mocks.invalidateQueries,
    }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({
        addToast: mocks.addToast,
    }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({ sessionGeneration: 7 }),
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({
        confirm: mocks.confirm,
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: mocks.loggerError,
    },
}))

function makeCategory(overrides: Partial<Category>): Category {
    return {
        categoryId: 1,
        name: 'General',
        sortOrder: 1,
        minWriteRole: 'USER',
        ...overrides,
    }
}

function createManager() {
    return useBoardCategoriesManager(ref('free-board'))
}

describe('useBoardCategoriesManager', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.confirm.mockResolvedValue(true)
    })

    it('loads categories sorted by sortOrder', async () => {
        vi.mocked(boardApi.getCategories).mockResolvedValueOnce(
            axiosApiResponse(
                apiSuccess([
                    makeCategory({ categoryId: 2, name: 'Second', sortOrder: 2 }),
                    makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
                ])
            )
        )

        const manager = createManager()

        await manager.fetchCategories()

        expect(boardApi.getCategories).toHaveBeenCalledWith(
            'free-board',
            expect.objectContaining({ signal: expect.anything() }),
        )
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 2])
        expect(manager.isLoading.value).toBe(false)
    })

    it('creates a category and resets the add form', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, isDefault: true, sortOrder: 1 }),
        ]
        manager.newCategoryName.value = '  Notice  '
        manager.newCategoryRole.value = 'BOARD_ADMIN'

        vi.mocked(boardApi.createCategory).mockResolvedValueOnce(
            axiosApiResponse(
                apiSuccess(makeCategory({
                    categoryId: 2,
                    name: 'Notice',
                    sortOrder: 2,
                    minWriteRole: 'BOARD_ADMIN',
                }))
            )
        )

        await manager.handleAdd()

        expect(boardApi.createCategory).toHaveBeenCalledWith('free-board', {
            name: 'Notice',
            minWriteRole: 'BOARD_ADMIN',
            sortOrder: 2,
        })
        expect(manager.categories.value).toHaveLength(2)
        expect(manager.newCategoryName.value).toBe('')
        expect(manager.newCategoryRole.value).toBe('USER')
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ['session', 7, 'board', 'categories', 'free-board'],
        })
    })

    it('does not delete when confirmation is cancelled', async () => {
        mocks.confirm.mockResolvedValueOnce(false)
        const manager = createManager()

        await manager.handleDelete(2)

        expect(boardApi.deleteCategory).not.toHaveBeenCalled()
        expect(mocks.invalidateQueries).not.toHaveBeenCalled()
    })

    it('deletes the category after confirmation', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'Notice' }),
        ]
        vi.mocked(boardApi.deleteCategory).mockResolvedValueOnce(
            axiosApiResponse(apiEmptySuccess())
        )

        await manager.handleDelete(2)

        expect(boardApi.deleteCategory).toHaveBeenCalledWith('free-board', 2)
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1])
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ['session', 7, 'board', 'categories', 'free-board'],
        })
    })

    it('updates the editing category and clears edit state', async () => {
        const manager = createManager()
        const category = makeCategory({ categoryId: 2, name: 'Old', sortOrder: 2 })
        manager.categories.value = [category]
        manager.startEdit(category)
        manager.editingName.value = '  New  '
        manager.editingRole.value = 'BOARD_ADMIN'

        vi.mocked(boardApi.updateCategory).mockResolvedValueOnce(
            axiosApiResponse(
                apiSuccess(makeCategory({
                    categoryId: 2,
                    name: 'New',
                    sortOrder: 2,
                    minWriteRole: 'BOARD_ADMIN',
                }))
            )
        )

        await manager.saveEdit(category)

        expect(boardApi.updateCategory).toHaveBeenCalledWith('free-board', 2, {
            name: 'New',
            sortOrder: 2,
            minWriteRole: 'BOARD_ADMIN',
            isDefault: undefined,
        })
        expect(manager.categories.value[0].name).toBe('New')
        expect(manager.editingId.value).toBeNull()
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ['session', 7, 'board', 'categories', 'free-board'],
        })
    })

    it('does not create or update categories with blank names', async () => {
        const manager = createManager()
        const category = makeCategory({ categoryId: 2, name: 'Old', sortOrder: 2 })

        manager.newCategoryName.value = '   '
        await manager.handleAdd()

        manager.startEdit(category)
        manager.editingName.value = '   '
        await manager.saveEdit(category)

        expect(boardApi.createCategory).not.toHaveBeenCalled()
        expect(boardApi.updateCategory).not.toHaveBeenCalled()
    })

    it('reorders all active categories with one request and applies the server order', async () => {
        const manager = createManager()
        manager.categories.value = initialCategories()
        vi.mocked(boardApi.reorderCategories).mockResolvedValue(
            reorderedResponse()
        )

        manager.onDragStart({ dataTransfer: { effectAllowed: 'copy' } } as unknown as DragEvent, 1)
        await manager.onDrop(0)

        expect(boardApi.reorderCategories).toHaveBeenCalledTimes(1)
        expect(boardApi.reorderCategories).toHaveBeenCalledWith('free-board', {
            categoryIds: [1, 3, 2],
        })
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ['session', 7, 'board', 'categories', 'free-board'],
        })
    })

    it('moves categories with the keyboard reorder API', async () => {
        const manager = createManager()
        manager.categories.value = initialCategories()
        vi.mocked(boardApi.reorderCategories).mockResolvedValue(reorderedResponse())

        expect(await manager.moveCategory(0, 1)).toBe(true)

        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])
        expect(boardApi.reorderCategories).toHaveBeenCalledTimes(1)
    })

    it('restores the snapshot when the atomic reorder fails', async () => {
        const manager = createManager()
        manager.categories.value = initialCategories()
        vi.mocked(boardApi.reorderCategories).mockRejectedValueOnce(new Error('failed'))

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        await manager.onDrop(0)

        expect(mocks.addToast).toHaveBeenCalledWith('board.category.orderFailed', 'error')
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 2, 3])
        expect(manager.isReordering.value).toBe(false)
        expect(mocks.invalidateQueries).not.toHaveBeenCalled()
    })

    it('ignores additional drops while a reorder request is pending', async () => {
        const manager = createManager()
        const reorderResponse = reorderedResponse()
        const reorder = createDeferred<typeof reorderResponse>()
        manager.categories.value = initialCategories()
        vi.mocked(boardApi.reorderCategories).mockReturnValueOnce(reorder.promise)

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        const firstDrop = manager.onDrop(0)

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 0)
        await manager.onDrop(1)

        expect(boardApi.reorderCategories).toHaveBeenCalledTimes(1)
        expect(manager.dragIndex.value).toBeNull()

        reorder.resolve(reorderResponse)
        await firstDrop

        expect(manager.isReordering.value).toBe(false)
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])
    })

    it('does not clear a newer board reorder when an older request settles', async () => {
        const boardUrl = ref('board-a')
        const manager = useBoardCategoriesManager(boardUrl)
        const first = createDeferred<ReturnType<typeof reorderedResponse>>()
        const second = createDeferred<ReturnType<typeof reorderedResponse>>()
        vi.mocked(boardApi.reorderCategories)
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise)

        manager.categories.value = initialCategories()
        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        const firstDrop = manager.onDrop(0)

        boardUrl.value = 'board-b'
        manager.resetState()
        manager.categories.value = initialCategories()
        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        const secondDrop = manager.onDrop(0)

        first.resolve(reorderedResponse())
        await firstDrop
        expect(manager.isReordering.value).toBe(true)

        second.resolve(reorderedResponse())
        await secondDrop
        expect(manager.isReordering.value).toBe(false)
    })

    it('aborts the previous request and keeps only the latest board response', async () => {
        const boardUrl = ref('board-a')
        const manager = useBoardCategoriesManager(boardUrl)
        const responseA = axiosApiResponse(apiSuccess([
            makeCategory({ categoryId: 10, name: 'A', sortOrder: 1 }),
        ]))
        const responseB = axiosApiResponse(apiSuccess([
            makeCategory({ categoryId: 20, name: 'B', sortOrder: 1 }),
        ]))
        const first = createDeferred<typeof responseA>()
        const second = createDeferred<typeof responseB>()
        vi.mocked(boardApi.getCategories)
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise)

        const firstLoad = manager.fetchCategories()
        const firstSignal = vi.mocked(boardApi.getCategories).mock.calls[0][1]?.signal
        boardUrl.value = 'board-b'
        const secondLoad = manager.fetchCategories()

        expect(firstSignal?.aborted).toBe(true)
        second.resolve(responseB)
        await secondLoad
        first.resolve(responseA)
        await firstLoad

        expect(manager.categories.value.map(category => category.categoryId)).toEqual([20])
    })
})

function initialCategories(): Category[] {
    return [
        makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
        makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
        makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
    ]
}

function reorderedResponse() {
    return axiosApiResponse(apiSuccess([
        makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
        makeCategory({ categoryId: 3, name: 'B', sortOrder: 2 }),
        makeCategory({ categoryId: 2, name: 'A', sortOrder: 3 }),
    ]))
}
