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
        isActive: true,
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

        expect(boardApi.getCategories).toHaveBeenCalledWith('free-board')
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
            queryKey: ['board', 'categories', expect.any(Object)],
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
            queryKey: ['board', 'categories', expect.any(Object)],
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
            isActive: true,
        })
        expect(manager.categories.value[0].name).toBe('New')
        expect(manager.editingId.value).toBeNull()
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ['board', 'categories', expect.any(Object)],
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

    it('reorders draggable categories after the default category', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
            makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
        ]
        vi.mocked(boardApi.updateCategory).mockResolvedValue(
            axiosApiResponse(apiSuccess(makeCategory({})))
        )

        manager.onDragStart({ dataTransfer: { effectAllowed: 'copy' } } as unknown as DragEvent, 1)
        await manager.onDrop(0)

        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])
        expect(boardApi.updateCategory).toHaveBeenCalledWith('free-board', 3, {
            name: 'B',
            sortOrder: 2,
            minWriteRole: 'USER',
            isDefault: undefined,
            isActive: true,
        })
        expect(boardApi.updateCategory).toHaveBeenCalledWith('free-board', 2, {
            name: 'A',
            sortOrder: 3,
            minWriteRole: 'USER',
            isDefault: undefined,
            isActive: true,
        })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            queryKey: ['board', 'categories', expect.any(Object)],
        })
    })

    it('moves categories with the keyboard reorder API', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
            makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
        ]
        vi.mocked(boardApi.updateCategory).mockResolvedValue(
            axiosApiResponse(apiSuccess(makeCategory({})))
        )

        expect(await manager.moveCategory(0, 1)).toBe(true)

        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])
        expect(boardApi.updateCategory).toHaveBeenCalledTimes(2)
    })

    it('reloads categories when reorder update fails', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
            makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
        ]
        vi.mocked(boardApi.updateCategory).mockRejectedValueOnce(new Error('failed'))
        vi.mocked(boardApi.getCategories).mockResolvedValueOnce(
            axiosApiResponse(
                apiSuccess([
                    makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
                    makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
                    makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
                ])
            )
        )

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        await manager.onDrop(0)

        expect(mocks.addToast).toHaveBeenCalledWith('board.category.orderFailed', 'error')
        expect(boardApi.getCategories).toHaveBeenCalledWith('free-board')
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 2, 3])
        expect(manager.isReordering.value).toBe(false)
        expect(mocks.invalidateQueries).not.toHaveBeenCalled()
    })

    it('keeps the rollback snapshot when reorder reload also fails', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
            makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
        ]
        vi.mocked(boardApi.updateCategory).mockRejectedValueOnce(new Error('failed'))
        vi.mocked(boardApi.getCategories).mockRejectedValueOnce(new Error('reload failed'))

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        await manager.onDrop(0)

        expect(mocks.addToast).toHaveBeenCalledWith('board.category.orderFailed', 'error')
        expect(boardApi.getCategories).toHaveBeenCalledWith('free-board')
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 2, 3])
        expect(manager.isReordering.value).toBe(false)
    })

    it('ignores additional drops while a reorder request is pending', async () => {
        const manager = createManager()
        const reorderResponse = axiosApiResponse(apiSuccess(makeCategory({})))
        const firstUpdate = createDeferred<typeof reorderResponse>()
        const secondUpdate = createDeferred<typeof reorderResponse>()
        manager.categories.value = [
            makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
            makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
        ]
        vi.mocked(boardApi.updateCategory)
            .mockReturnValueOnce(firstUpdate.promise)
            .mockReturnValueOnce(secondUpdate.promise)

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        const firstDrop = manager.onDrop(0)

        expect(manager.isReordering.value).toBe(true)
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 0)
        await manager.onDrop(1)

        expect(boardApi.updateCategory).toHaveBeenCalledTimes(2)
        expect(manager.dragIndex.value).toBeNull()

        firstUpdate.resolve(reorderResponse)
        secondUpdate.resolve(reorderResponse)
        await firstDrop

        expect(manager.isReordering.value).toBe(false)
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 3, 2])
    })
})
