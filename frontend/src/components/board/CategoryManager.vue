<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { boardApi } from '@/api/board'
import { Trash2, Edit2, Check, X, Plus, GripVertical } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseSelect from '@/components/common/BaseSelect.vue'
import { useConfirm } from '@/composables/useConfirm'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const props = defineProps<{
  boardUrl: string
}>()

interface Category {
  categoryId: number
  name: string
  sortOrder: number
  isActive: boolean
  minWriteRole: string
}

const roles = [
  { value: 'USER', label: 'User' },
  { value: 'BOARD_ADMIN', label: 'Board Admin' },
  { value: 'SUPER_ADMIN', label: 'Super Admin' }
]

const categories = ref<Category[]>([])
const isLoading = ref(true)
const error = ref('')
const newCategoryName = ref('')
const newCategoryRole = ref('USER')
const editingId = ref<number | null>(null)
const editingName = ref('')
const editingRole = ref('USER')
const dragIndex = ref<number | null>(null)

const generalCategory = computed(() => categories.value.find(c => c.name === '일반'))
const draggableCategories = computed(() => categories.value.filter(c => c.name !== '일반'))

async function fetchCategories() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getCategories(props.boardUrl)
    if (data.success) {
      categories.value = data.data.sort((a: Category, b: Category) => a.sortOrder - b.sortOrder)
    }
  } catch (err) {
    logger.error('Failed to load categories:', err)
    error.value = t('board.category.loadFailed')
  } finally {
    isLoading.value = false
  }
}

async function handleAdd() {
  if (!newCategoryName.value.trim()) return

  try {
    const { data } = await boardApi.createCategory(props.boardUrl, {
      name: newCategoryName.value,
      minWriteRole: newCategoryRole.value,
      sortOrder: categories.value.length + 1
    })
    if (data.success) {
      categories.value.push(data.data)
      newCategoryName.value = ''
      newCategoryRole.value = 'USER'
    }
  } catch (err) {
    logger.error('Failed to create category:', err)
    toastStore.addToast(t('board.category.createFailed'), 'error')
  }
}

async function handleDelete(categoryId: number) {
  const isConfirmed = await confirm(t('board.category.deleteConfirm'))
  if (!isConfirmed) return

  try {
    const { data } = await boardApi.deleteCategory(props.boardUrl, categoryId)
    if (data.success) {
      categories.value = categories.value.filter(c => c.categoryId !== categoryId)
    }
  } catch (err) {
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
    const { data } = await boardApi.updateCategory(props.boardUrl, category.categoryId, {
      name: editingName.value,
      sortOrder: category.sortOrder,
      minWriteRole: editingRole.value,
      isActive: true
    })
    if (data.success) {
      const index = categories.value.findIndex(c => c.categoryId === category.categoryId)
      if (index !== -1) {
        categories.value[index] = data.data
      }
      cancelEdit()
    }
  } catch (err) {
    logger.error('Failed to update category:', err)
    toastStore.addToast(t('board.category.updateFailed'), 'error')
  }
}

function onDragStart(event: DragEvent, index: number) {
  dragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

async function onDrop(index: number) {
  const fromIndex = dragIndex.value
  const toIndex = index

  if (fromIndex === null || fromIndex === toIndex) return

  // Operate on draggableCategories logic
  const newDraggables = [...draggableCategories.value]
  const [movedItem] = newDraggables.splice(fromIndex, 1)
  newDraggables.splice(toIndex, 0, movedItem)

  // Reconstruct full list: General (if exists) + Reordered Draggables
  const newCategories: Category[] = []
  if (generalCategory.value) newCategories.push(generalCategory.value)
  newCategories.push(...newDraggables)

  categories.value = newCategories
  dragIndex.value = null

  // Update sortOrder for all categories
  try {
    const updatePromises = categories.value.map((cat, idx) => {
      // If sortOrder is already correct, skip (optimization)
      if (cat.sortOrder === idx + 1) return Promise.resolve()

      // Update local state first to reflect sortOrder immediately
      cat.sortOrder = idx + 1

      return boardApi.updateCategory(props.boardUrl, cat.categoryId, {
        name: cat.name,
        sortOrder: idx + 1,
        minWriteRole: cat.minWriteRole,
        isActive: cat.isActive
      })
    })
    await Promise.all(updatePromises)
  } catch (err) {
    logger.error('Failed to reorder categories:', err)
    toastStore.addToast(t('board.category.orderFailed'), 'error')
    fetchCategories() // Revert changes
  }
}

onMounted(fetchCategories)
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('common.category') }}</h3>

    <!-- Add Category -->
    <form @submit.prevent="handleAdd" class="flex gap-2">
      <BaseInput v-model="newCategoryName" :placeholder="$t('board.category.placeholder.new')" hideLabel
        inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600 dark:placeholder-gray-400" class="flex-1" />
      <BaseSelect v-model="newCategoryRole" class="w-32"
        inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
        <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
      </BaseSelect>
      <BaseButton type="submit" variant="primary" class="px-3">
        <Plus class="h-4 w-4" />
      </BaseButton>
    </form>

    <!-- Category List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div
      class="border border-gray-200 dark:border-gray-700 rounded-md divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800"
      v-else>
      <!-- General Category (Static) -->
      <div v-if="generalCategory" class="px-4 py-3 flex items-center justify-between bg-gray-50 dark:bg-gray-900/50">
        <div class="flex items-center text-gray-400 dark:text-gray-500 cursor-not-allowed p-1 mr-3">
          <GripVertical class="h-4 w-4" />
        </div>

        <div v-if="editingId === generalCategory.categoryId" class="flex-1 flex items-center gap-2">
          <span class="text-sm text-gray-900 dark:text-gray-200 font-medium">{{ generalCategory.name }} {{
            $t('board.category.default') }}</span>
          <div class="ml-auto flex items-center gap-2">
            <BaseSelect v-model="editingRole" class="w-32"
              inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
              <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
            </BaseSelect>
            <BaseButton @click="saveEdit(generalCategory)" variant="ghost" size="sm"
              class="p-1 text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300">
              <Check class="h-4 w-4" />
            </BaseButton>
            <BaseButton @click="cancelEdit" variant="ghost" size="sm"
              class="p-1 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300">
              <X class="h-4 w-4" />
            </BaseButton>
          </div>
        </div>

        <div v-else class="flex-1 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-900 dark:text-gray-200 font-medium">{{ generalCategory.name }} {{
              $t('board.category.default') }}</span>
            <span
              class="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">{{
                generalCategory.minWriteRole || 'USER' }}</span>
          </div>
          <BaseButton @click="startEdit(generalCategory)" variant="ghost" size="sm"
            class="p-1 text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-300">
            <Edit2 class="h-4 w-4" />
          </BaseButton>
        </div>
      </div>

      <!-- Draggable List -->
      <transition-group name="list" tag="ul" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="(category, index) in draggableCategories" :key="category.categoryId"
          class="px-4 py-3 flex items-center justify-between group bg-white dark:bg-gray-800" @dragover.prevent
          @dragenter.prevent @drop="onDrop(index)">
          <div class="flex items-center">
            <div draggable="true" @dragstart="onDragStart($event, index)"
              class="mr-3 cursor-move text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
              <GripVertical class="h-4 w-4" />
            </div>
          </div>

          <div v-if="editingId === category.categoryId" class="flex-1 flex items-center gap-2">
            <BaseInput v-model="editingName" hideLabel
              inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600" class="w-full" />
            <div class="ml-auto flex items-center gap-2">
              <BaseSelect v-model="editingRole" class="w-32"
                inputClass="dark:bg-gray-700 dark:text-white dark:border-gray-600">
                <option v-for="role in roles" :key="role.value" :value="role.value">{{ role.label }}</option>
              </BaseSelect>
              <BaseButton @click="saveEdit(category)" variant="ghost" size="sm"
                class="p-1 text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300">
                <Check class="h-4 w-4" />
              </BaseButton>
              <BaseButton @click="cancelEdit" variant="ghost" size="sm"
                class="p-1 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300">
                <X class="h-4 w-4" />
              </BaseButton>
            </div>
          </div>
          <div v-else class="flex-1 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-900 dark:text-gray-200">{{ category.name }}</span>
              <span
                class="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded-full">{{
                  category.minWriteRole }}</span>
            </div>
            <div class="flex items-center gap-2">
              <BaseButton @click="startEdit(category)" variant="ghost" size="sm"
                class="p-1 text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-300">
                <Edit2 class="h-4 w-4" />
              </BaseButton>
              <BaseButton @click="handleDelete(category.categoryId)" variant="ghost" size="sm"
                class="p-1 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300">
                <Trash2 class="h-4 w-4" />
              </BaseButton>
            </div>
          </div>
        </li>
      </transition-group>
    </div>
    <div v-if="!isLoading && categories.length === 0" class="text-sm text-gray-500 dark:text-gray-400 text-center">
      {{ $t('board.category.empty') }}
    </div>
  </div>
</template>

<style scoped>
.list-move,
.list-enter-active,
.list-leave-active {
  transition: all 0.5s ease;
}

.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

.list-leave-active {
  position: absolute;
}
</style>
