import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { boardApi } from '@/api/board'
import { unwrapApiData } from '@/api/response'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { resolveDefaultCategory } from '@/utils/board'
import type { Category } from '@/types'

export function useBoardCategoriesManager(boardUrl: Readonly<Ref<string>>) {
    const { t } = useI18n()
    const toastStore = useToastStore()
    const { confirm } = useConfirm()

    const categories = ref<Category[]>([])
    const isLoading = ref(true)
    const error = ref('')
    const newCategoryName = ref('')
    const newCategoryRole = ref('USER')
    const editingId = ref<number | null>(null)
    const editingName = ref('')
    const editingRole = ref('USER')
    const dragIndex = ref<number | null>(null)
    const isReordering = ref(false)

    const defaultCategory = computed(() => resolveDefaultCategory(categories.value))
    const draggableCategories = computed(() =>
        categories.value.filter(category => category.categoryId !== defaultCategory.value?.categoryId)
    )

    async function fetchCategories() {
        isLoading.value = true
        try {
            const { data } = await boardApi.getCategories(boardUrl.value)
            if (data.success) {
                categories.value = unwrapApiData(data).sort((left, right) => left.sortOrder - right.sortOrder)
            }
        } catch (err: unknown) {
            logger.error('Failed to load categories:', err)
            error.value = t('board.category.loadFailed')
        } finally {
            isLoading.value = false
        }
    }

    async function handleAdd() {
        if (!newCategoryName.value.trim()) return

        try {
            const { data } = await boardApi.createCategory(boardUrl.value, {
                name: newCategoryName.value,
                minWriteRole: newCategoryRole.value,
                sortOrder: categories.value.length + 1,
            })
            if (data.success) {
                categories.value.push(unwrapApiData(data))
                newCategoryName.value = ''
                newCategoryRole.value = 'USER'
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
        if (!editingName.value.trim()) return

        try {
            const { data } = await boardApi.updateCategory(boardUrl.value, category.categoryId, {
                name: editingName.value,
                sortOrder: category.sortOrder,
                minWriteRole: editingRole.value,
                isDefault: category.isDefault,
                isActive: true,
            })
            if (data.success) {
                const index = categories.value.findIndex(item => item.categoryId === category.categoryId)
                if (index !== -1) {
                    categories.value[index] = unwrapApiData(data)
                }
                cancelEdit()
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

    async function onDrop(index: number) {
        if (isReordering.value) {
            dragIndex.value = null
            return
        }

        const fromIndex = dragIndex.value
        const toIndex = index

        if (fromIndex === null || fromIndex === toIndex) {
            dragIndex.value = null
            return
        }

        const previousCategories = categories.value.map(category => ({ ...category }))
        const previousSortById = new Map(
            previousCategories.map(category => [category.categoryId, category.sortOrder])
        )
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

        try {
            const updatePromises = categories.value.map((category, idx) => {
                if (previousSortById.get(category.categoryId) === idx + 1) return Promise.resolve()

                return boardApi.updateCategory(boardUrl.value, category.categoryId, {
                    name: category.name,
                    sortOrder: idx + 1,
                    minWriteRole: category.minWriteRole,
                    isDefault: category.isDefault,
                    isActive: category.isActive,
                })
            })
            await Promise.all(updatePromises)
        } catch (err: unknown) {
            logger.error('Failed to reorder categories:', err)
            toastStore.addToast(t('board.category.orderFailed'), 'error')
            categories.value = previousCategories
            await fetchCategories()
        } finally {
            isReordering.value = false
        }
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
        handleAdd,
        handleDelete,
        startEdit,
        cancelEdit,
        saveEdit,
        onDragStart,
        onDrop,
    }
}
