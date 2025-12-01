<script setup>
import { ref, onMounted, computed } from 'vue'
import { boardApi } from '@/api/board'
import { Trash2, Edit2, Check, X, Plus, GripVertical } from 'lucide-vue-next'

const props = defineProps({
  boardUrl: {
    type: String,
    required: true
  }
})

const categories = ref([])
const isLoading = ref(true)
const error = ref('')
const newCategoryName = ref('')
const editingId = ref(null)
const editingName = ref('')
const dragIndex = ref(null)

const generalCategory = computed(() => categories.value.find(c => c.name === '일반'))
const draggableCategories = computed(() => categories.value.filter(c => c.name !== '일반'))

async function fetchCategories() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getCategories(props.boardUrl)
    if (data.success) {
      categories.value = data.data.sort((a, b) => a.sortOrder - b.sortOrder)
    }
  } catch (err) {
    console.error('Failed to load categories:', err)
    error.value = 'Failed to load categories.'
  } finally {
    isLoading.value = false
  }
}

async function handleAdd() {
  if (!newCategoryName.value.trim()) return

  try {
    const { data } = await boardApi.createCategory(props.boardUrl, {
      name: newCategoryName.value,
      sortOrder: categories.value.length + 1
    })
    if (data.success) {
      categories.value.push(data.data)
      newCategoryName.value = ''
    }
  } catch (err) {
    console.error('Failed to create category:', err)
    alert('Failed to create category.')
  }
}

async function handleDelete(categoryId) {
  if (!confirm('Are you sure you want to delete this category?')) return

  try {
    const { data } = await boardApi.deleteCategory(props.boardUrl, categoryId)
    if (data.success) {
      categories.value = categories.value.filter(c => c.categoryId !== categoryId)
    }
  } catch (err) {
    console.error('Failed to delete category:', err)
    alert('Failed to delete category.')
  }
}

function startEdit(category) {
  editingId.value = category.categoryId
  editingName.value = category.name
}

function cancelEdit() {
  editingId.value = null
  editingName.value = ''
}

async function saveEdit(category) {
  if (!editingName.value.trim()) return

  try {
    const { data } = await boardApi.updateCategory(props.boardUrl, category.categoryId, {
      name: editingName.value,
      sortOrder: category.sortOrder,
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
    console.error('Failed to update category:', err)
    alert('Failed to update category.')
  }
}

function onDragStart(event, index) {
  dragIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
}

async function onDrop(index) {
  const fromIndex = dragIndex.value
  const toIndex = index

  if (fromIndex === null || fromIndex === toIndex) return

  // Operate on draggableCategories logic
  const newDraggables = [...draggableCategories.value]
  const [movedItem] = newDraggables.splice(fromIndex, 1)
  newDraggables.splice(toIndex, 0, movedItem)

  // Reconstruct full list: General (if exists) + Reordered Draggables
  const newCategories = []
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
        sortOrder: idx + 1
      })
    })
    await Promise.all(updatePromises)
  } catch (err) {
    console.error('Failed to reorder categories:', err)
    alert('Failed to update category order.')
    fetchCategories() // Revert changes
  }
}

onMounted(fetchCategories)
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-lg font-medium leading-6 text-gray-900">Categories</h3>
    
    <!-- Add Category -->
    <form @submit.prevent="handleAdd" class="flex gap-2">
      <input
        type="text"
        v-model="newCategoryName"
        placeholder="New category name"
        class="input-base"
      />
      <button
        type="submit"
        class="btn-primary"
      >
        <Plus class="h-4 w-4" />
      </button>
    </form>

    <!-- Category List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div class="border border-gray-200 rounded-md divide-y divide-gray-200 bg-white" v-else>
        <!-- General Category (Static) -->
        <div 
            v-if="generalCategory"
            class="px-4 py-3 flex items-center justify-between bg-gray-50"
        >
            <div class="flex items-center text-gray-400 cursor-not-allowed p-1 mr-3">
                <GripVertical class="h-4 w-4" />
            </div>
            <div class="flex-1 flex items-center justify-between">
                <span class="text-sm text-gray-900 font-medium">{{ generalCategory.name }} (기본)</span>
                <!-- No actions for General -->
            </div>
        </div>

        <!-- Draggable List -->
        <transition-group 
          name="list" 
          tag="ul" 
          class="divide-y divide-gray-200"
        >
          <li 
            v-for="(category, index) in draggableCategories" 
            :key="category.categoryId" 
            class="px-4 py-3 flex items-center justify-between group bg-white"
            @dragover.prevent
            @dragenter.prevent
            @drop="onDrop(index)"
          >
            <div class="flex items-center">
              <div 
                draggable="true"
                @dragstart="onDragStart($event, index)"
                class="mr-3 cursor-move text-gray-400 hover:text-gray-600 p-1 rounded hover:bg-gray-100"
              >
                <GripVertical class="h-4 w-4" />
              </div>
            </div>
    
            <div v-if="editingId === category.categoryId" class="flex-1 flex items-center gap-2">
              <input
                type="text"
                v-model="editingName"
                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
              />
              <button @click="saveEdit(category)" class="text-green-600 hover:text-green-800">
                <Check class="h-4 w-4" />
              </button>
              <button @click="cancelEdit" class="text-gray-500 hover:text-gray-700">
                <X class="h-4 w-4" />
              </button>
            </div>
            <div v-else class="flex-1 flex items-center justify-between">
              <span class="text-sm text-gray-900">{{ category.name }}</span>
              <div class="flex items-center gap-2">
                <button @click="startEdit(category)" class="text-indigo-600 hover:text-indigo-800">
                  <Edit2 class="h-4 w-4" />
                </button>
                <button @click="handleDelete(category.categoryId)" class="text-red-600 hover:text-red-800">
                  <Trash2 class="h-4 w-4" />
                </button>
              </div>
            </div>
          </li>
        </transition-group>
    </div>
    <div v-if="!isLoading && categories.length === 0" class="text-sm text-gray-500 text-center">
        No categories yet.
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
