import { computed, getCurrentScope, onScopeDispose, ref, type Ref } from 'vue'
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
import { sessionQueryKey, subscribeAuthSessionBoundary } from '@/queryAuthScope'

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
    const isMutating = ref(false)
    let reorderGeneration = 0
    let reorderController: AbortController | null = null
    let mutationRevision = 0
    let mutationController: AbortController | null = null

    interface CategoryMutationIntent {
        boardUrl: string
        sessionGeneration: number
        revision: number
        controller: AbortController
    }

    const cancelMutation = () => {
        mutationRevision += 1
        mutationController?.abort()
        mutationController = null
        isMutating.value = false
    }

    const cancelReorder = () => {
        reorderGeneration += 1
        reorderController?.abort()
        reorderController = null
        isReordering.value = false
        dragIndex.value = null
    }

    const beginMutation = (): CategoryMutationIntent | null => {
        if (isMutating.value || isReordering.value) return null
        const controller = new AbortController()
        const intent = {
            boardUrl: boardUrl.value,
            sessionGeneration: authStore.sessionGeneration,
            revision: ++mutationRevision,
            controller,
        }
        mutationController = controller
        isMutating.value = true
        return intent
    }

    const isMutationCurrent = (intent: CategoryMutationIntent) =>
        mutationController === intent.controller
        && mutationRevision === intent.revision
        && !intent.controller.signal.aborted
        && boardUrl.value === intent.boardUrl
        && authStore.sessionGeneration === intent.sessionGeneration

    const finishMutation = (intent: CategoryMutationIntent) => {
        if (mutationController !== intent.controller) return
        mutationController = null
        isMutating.value = false
    }

    const stopSessionBoundary = subscribeAuthSessionBoundary(resetState)
    if (getCurrentScope()) onScopeDispose(stopSessionBoundary)

    const defaultCategory = computed(() => resolveDefaultCategory(categories.value))
    const draggableCategories = computed(() =>
        categories.value.filter(category => category.categoryId !== defaultCategory.value?.categoryId)
    )
    const invalidateCategories = (targetBoardUrl = boardUrl.value, generation = authStore.sessionGeneration) => {
        queryClient.invalidateQueries({
            queryKey: sessionQueryKey(generation, boardQueryKeys.categories(targetBoardUrl)),
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
        cancelMutation()
        cancelReorder()
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

        const intent = beginMutation()
        if (!intent) return
        try {
            const nextSortOrder = categories.value.reduce(
                (maxSortOrder, category) => Math.max(maxSortOrder, category.sortOrder),
                0,
            ) + 1
            const { data } = await boardApi.createCategory(intent.boardUrl, {
                name,
                minWriteRole: newCategoryRole.value,
                sortOrder: nextSortOrder,
            }, { signal: intent.controller.signal })
            if (data.success && isMutationCurrent(intent)) {
                categories.value.push(unwrapApiData(data))
                newCategoryName.value = ''
                newCategoryRole.value = 'USER'
                invalidateCategories(intent.boardUrl, intent.sessionGeneration)
            }
        } catch (err: unknown) {
            if (!isMutationCurrent(intent)) return
            logger.error('Failed to create category:', err)
            toastStore.addToast(t('board.category.createFailed'), 'error')
        } finally {
            finishMutation(intent)
        }
    }

    async function handleDelete(categoryId: number) {
        const intent = beginMutation()
        if (!intent) return
        const isConfirmed = await confirm(t('board.category.deleteConfirm'))
        if (!isConfirmed || !isMutationCurrent(intent)) {
            finishMutation(intent)
            return
        }

        try {
            const { data } = await boardApi.deleteCategory(intent.boardUrl, categoryId, { signal: intent.controller.signal })
            if (data.success && isMutationCurrent(intent)) {
                categories.value = categories.value.filter(category => category.categoryId !== categoryId)
                invalidateCategories(intent.boardUrl, intent.sessionGeneration)
            }
        } catch (err: unknown) {
            if (!isMutationCurrent(intent)) return
            logger.error('Failed to delete category:', err)
            toastStore.addToast(t('board.category.deleteFailed'), 'error')
        } finally {
            finishMutation(intent)
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

        const intent = beginMutation()
        if (!intent) return
        try {
            const { data } = await boardApi.updateCategory(intent.boardUrl, category.categoryId, {
                name,
                sortOrder: category.sortOrder,
                minWriteRole: editingRole.value,
                isDefault: category.isDefault,
            }, { signal: intent.controller.signal })
            if (data.success && isMutationCurrent(intent)) {
                const index = categories.value.findIndex(item => item.categoryId === category.categoryId)
                if (index !== -1) {
                    categories.value[index] = unwrapApiData(data)
                }
                cancelEdit()
                invalidateCategories(intent.boardUrl, intent.sessionGeneration)
            }
        } catch (err: unknown) {
            if (!isMutationCurrent(intent)) return
            logger.error('Failed to update category:', err)
            toastStore.addToast(t('board.category.updateFailed'), 'error')
        } finally {
            finishMutation(intent)
        }
    }

    function onDragStart(event: DragEvent, index: number) {
        if (isReordering.value || isMutating.value) return

        dragIndex.value = index
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move'
        }
    }

    async function onDrop(index: number): Promise<boolean> {
        if (isReordering.value || isMutating.value) {
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
        const requestedSessionGeneration = authStore.sessionGeneration
        const currentReorderGeneration = ++reorderGeneration
        const controller = new AbortController()
        reorderController?.abort()
        reorderController = controller
        const isCurrentReorder = () =>
            reorderController === controller
            && currentReorderGeneration === reorderGeneration
            && !controller.signal.aborted
            && boardUrl.value === requestedBoardUrl
            && authStore.sessionGeneration === requestedSessionGeneration

        try {
            const { data } = await boardApi.reorderCategories(requestedBoardUrl, {
                categoryIds: categories.value.map(category => category.categoryId),
            }, { signal: controller.signal })
            if (!isCurrentReorder()) return false
            if (!data.success) throw new Error('Category reorder failed')
            categories.value = unwrapApiData(data).sort((left, right) => left.sortOrder - right.sortOrder)
            invalidateCategories(requestedBoardUrl, requestedSessionGeneration)
            return true
        } catch (err: unknown) {
            if (!isCurrentReorder()) return false
            logger.error('Failed to reorder categories:', err)
            toastStore.addToast(t('board.category.orderFailed'), 'error')
            categories.value = previousCategories
            return false
        } finally {
            if (reorderController === controller && currentReorderGeneration === reorderGeneration) {
                reorderController = null
                isReordering.value = false
            }
        }
    }

    async function moveCategory(index: number, offset: -1 | 1): Promise<boolean> {
        if (isMutating.value || isReordering.value) return false
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
        isMutating,
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
