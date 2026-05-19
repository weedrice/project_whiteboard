import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardCategoriesManager } from '../useBoardCategoriesManager'
import { boardApi } from '@/api/board'
import type { Category } from '@/types'
import type { ApiResponse } from '@/types'
import type { AxiosResponse } from 'axios'

const mocks = vi.hoisted(() => ({
    addToast: vi.fn(),
    confirm: vi.fn(),
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

function apiResponse<T>(data: ApiResponse<T>): AxiosResponse<ApiResponse<T>> {
    return {
        data,
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {
            headers: undefined,
        },
    } as unknown as AxiosResponse<ApiResponse<T>>
}

describe('useBoardCategoriesManager', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.confirm.mockResolvedValue(true)
    })

    it('loads categories sorted by sortOrder', async () => {
        vi.mocked(boardApi.getCategories).mockResolvedValueOnce(
            apiResponse({
                success: true,
                data: [
                    makeCategory({ categoryId: 2, name: 'Second', sortOrder: 2 }),
                    makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
                ],
            })
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
        manager.newCategoryName.value = 'Notice'
        manager.newCategoryRole.value = 'BOARD_ADMIN'

        vi.mocked(boardApi.createCategory).mockResolvedValueOnce(
            apiResponse({
                success: true,
                data: makeCategory({
                    categoryId: 2,
                    name: 'Notice',
                    sortOrder: 2,
                    minWriteRole: 'BOARD_ADMIN',
                }),
            })
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
    })

    it('does not delete when confirmation is cancelled', async () => {
        mocks.confirm.mockResolvedValueOnce(false)
        const manager = createManager()

        await manager.handleDelete(2)

        expect(boardApi.deleteCategory).not.toHaveBeenCalled()
    })

    it('deletes the category after confirmation', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'Notice' }),
        ]
        vi.mocked(boardApi.deleteCategory).mockResolvedValueOnce(
            apiResponse({
                success: true,
                data: undefined,
            })
        )

        await manager.handleDelete(2)

        expect(boardApi.deleteCategory).toHaveBeenCalledWith('free-board', 2)
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1])
    })

    it('updates the editing category and clears edit state', async () => {
        const manager = createManager()
        const category = makeCategory({ categoryId: 2, name: 'Old', sortOrder: 2 })
        manager.categories.value = [category]
        manager.startEdit(category)
        manager.editingName.value = 'New'
        manager.editingRole.value = 'BOARD_ADMIN'

        vi.mocked(boardApi.updateCategory).mockResolvedValueOnce(
            apiResponse({
                success: true,
                data: makeCategory({
                    categoryId: 2,
                    name: 'New',
                    sortOrder: 2,
                    minWriteRole: 'BOARD_ADMIN',
                }),
            })
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
    })

    it('reorders draggable categories after the default category', async () => {
        const manager = createManager()
        manager.categories.value = [
            makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
            makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
            makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
        ]
        vi.mocked(boardApi.updateCategory).mockResolvedValue(
            apiResponse({
                success: true,
                data: makeCategory({}),
            })
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
            apiResponse({
                success: true,
                data: [
                    makeCategory({ categoryId: 1, name: 'General', sortOrder: 1, isDefault: true }),
                    makeCategory({ categoryId: 2, name: 'A', sortOrder: 2 }),
                    makeCategory({ categoryId: 3, name: 'B', sortOrder: 3 }),
                ],
            })
        )

        manager.onDragStart({ dataTransfer: null } as unknown as DragEvent, 1)
        await manager.onDrop(0)

        expect(mocks.addToast).toHaveBeenCalledWith('board.category.orderFailed', 'error')
        expect(boardApi.getCategories).toHaveBeenCalledWith('free-board')
        expect(manager.categories.value.map(category => category.categoryId)).toEqual([1, 2, 3])
    })
})
