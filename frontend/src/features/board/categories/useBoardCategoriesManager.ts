import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQueryClient } from '@tanstack/vue-query'
import { boardApi } from '@/api/board'
import { unwrapApiData } from '@/api/response'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { useConfirm } from '@/composables/useConfirm'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { resolveDefaultCategory } from '@/utils/board'
import type { Category } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey } from '@/queryAuthScope'

export function useBoardCategoriesManager(boardUrl: Readonly<Ref<string>>) {
    const { t } = useI18n()
    const toastStore = useToastStore()
    const { confirm } = useConfirm()
    const queryClient = useQueryClient()
    const authStore = useAuthStore()

    const categories = ref<Category[]>([])
    const categoryLoadTask = useLatestAsyncTask<string>({
        getErrorValue: () => t('board.category.loadFailed'),
        onError: (caughtError) => logger.error('Failed to load categories:', caughtError),
    })
    const isLoading = categoryLoadTask.loading
    const error = categoryLoadTask.error
    const newCategoryName = ref('')
    const newCategoryRole = ref('USER')
    const editingId = ref<number | null>(null)
    const editingName = ref('')
    const editingRole = ref('USER')
    const dragIndex = ref<number | null>(null)
    const isReordering = ref(false)
    let reorderGeneration = 0

    const defaultCategory = computed(() => resolveDefaultCategory(categories.value))
    const draggableCategories = computed(() =>
        categories.value.filter(category => category.categoryId !== defaultCategory.value?.categoryId)
    )
    const invalidateCategories = (targetBoardUrl = boardUrl.value) => {
        queryClient.invalidateQueries({
            queryKey: currentSessionQueryKey(authStore, boardQueryKeys.categories(targetBoardUrl)),
        })
    }

    async function fetchCategories() {
        const requestedBoardUrl = boardUrl.value
        const loadedCategories = await categoryLoadTask.run(async ({ signal }) => {
            const { data } = await boardApi.getCategories(requestedBoardUrl, { signal })
            if (!data.success) throw new Error('Category load failed')
            return unwrapApiData(data).sort((left, right) => left.sortOrder - right.sortOrder)
        })
        if (loadedCategories) {
            categories.value = loadedCategories
        }
    }

    function resetState() {
        categoryLoadTask.reset()
        reorderGeneration += 1
        categories.value = []
        newCategoryName.value = ''
        newCategoryRole.value = 'USER'
        editingId.value = null
        editingName.value = ''
        editingRole.value = 'USER'
        dragIndex.value = null
        isReordering.value = false
    }

    async function handleAdd() {
        const name = newCategoryName.value.trim()
        if (!name) return

        try {
            const { data } = await boardApi.createCategory(boardUrl.value, {
                name,
                minWriteRole: newCategoryRole.value,
                sortOrder: categories.value.length + 1,
            })
            if (data.success) {
                categories.value.push(unwrapApiData(data))
                newCategoryName.value = ''
                newCategoryRole.value = 'USER'
                invalidateCategories()
            }
        } catch (err: unknown) {
            logger.error('Failed to create category:', err)
            toastStore.addToast(t('board.category.createFailed'), 'error')
        }
    }

    async function handleDelete(categoryId: number) {
        const isConfirmed = await confirm(t('board.category.deleteConfirm'))
        if (!isConfirmed) return

        try {
            const { data } = await boardApi.deleteCategory(boardUrl.value, categoryId)
            if (data.success) {
                categories.value = categories.value.filter(category => category.categoryId !== categoryId)
                invalidateCategories()
            }
        } catch (err: unknown) {
            logger.error('Failed to delete category:', err)
            toastStore.addToast(t('board.category.deleteFailed'), 'error')
        }
    }

    function startEdit(category: Category) {
        editingId.value = category.categoryId
        editingName.value = category.name
        editingRole.value = category.minWriteRole || 'USER'
    }

    function cancelEdit() {
        editingId.value = null
        editingName.value = ''
        editingRole.value = 'USER'
    }

    async function saveEdit(category: Category) {
        const name = editingName.value.trim()
        if (!name) return

        try {
            const { data } = await boardApi.updateCategory(boardUrl.value, category.categoryId, {
                name,
                sortOrder: category.sortOrder,
                minWriteRole: editingRole.value,
                isDefault: category.isDefault,
            })
            if (data.success) {
                const index = categories.value.findIndex(item => item.categoryId === category.categoryId)
                if (index !== -1) {
                    categories.value[index] = unwrapApiData(data)
                }
                cancelEdit()
                invalidateCategories()
            }
        } catch (err: unknown) {
            logger.error('Failed to update category:', err)
            toastStore.addToast(t('board.category.updateFailed'), 'error')
        }
    }

    function onDragStart(event: DragEvent, index: number) {
        if (isReordering.value) return

        dragIndex.value = index
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move'
        }
    }

    async function onDrop(index: number): Promise<boolean> {
        if (isReordering.value) {
            dragIndex.value = null
            return false
        }

        const fromIndex = dragIndex.value
        const toIndex = index

        if (fromIndex === null || fromIndex === toIndex) {
            dragIndex.value = null
            return false
        }

        const previousCategories = categories.value.map(category => ({ ...category }))
        const newDraggables = [...draggableCategories.value]
        const [movedItem] = newDraggables.splice(fromIndex, 1)
        newDraggables.splice(toIndex, 0, movedItem)

        const orderedCategories: Category[] = []
        if (defaultCategory.value) orderedCategories.push(defaultCategory.value)
        orderedCategories.push(...newDraggables)

        const newCategories = orderedCategories.map((category, idx) => ({
            ...category,
            sortOrder: idx + 1,
        }))

        categories.value = newCategories
        dragIndex.value = null
        isReordering.value = true
        const requestedBoardUrl = boardUrl.value
        const currentReorderGeneration = ++reorderGeneration

        try {
            const { data } = await boardApi.reorderCategories(requestedBoardUrl, {
                categoryIds: categories.value.map(category => category.categoryId),
            })
            if (boardUrl.value !== requestedBoardUrl) return false
            if (!data.success) throw new Error('Category reorder failed')
            categories.value = unwrapApiData(data).sort((left, right) => left.sortOrder - right.sortOrder)
            invalidateCategories(requestedBoardUrl)
            return true
        } catch (err: unknown) {
            if (boardUrl.value !== requestedBoardUrl) return false
            logger.error('Failed to reorder categories:', err)
            toastStore.addToast(t('board.category.orderFailed'), 'error')
            categories.value = previousCategories
            return false
        } finally {
            if (currentReorderGeneration === reorderGeneration) {
                isReordering.value = false
            }
        }
    }

    async function moveCategory(index: number, offset: -1 | 1): Promise<boolean> {
        const targetIndex = index + offset
        if (targetIndex < 0 || targetIndex >= draggableCategories.value.length) return false

        dragIndex.value = index
        return onDrop(targetIndex)
    }

    return {
        categories,
        isLoading,
        error,
        newCategoryName,
        newCategoryRole,
        editingId,
        editingName,
        editingRole,
        dragIndex,
        isReordering,
        defaultCategory,
        draggableCategories,
        fetchCategories,
        resetState,
        handleAdd,
        handleDelete,
        startEdit,
        cancelEdit,
        saveEdit,
        onDragStart,
        onDrop,
        moveCategory,
    }
}
