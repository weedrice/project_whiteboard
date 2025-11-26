<script setup>
import { ref, onMounted } from 'vue'
import { boardApi } from '@/api/board'
import { Trash2, Edit2, Check, X, Plus } from 'lucide-vue-next'

const props = defineProps({
  boardId: {
    type: [String, Number],
    required: true
  }
})

const categories = ref([])
const isLoading = ref(true)
const error = ref('')
const newCategoryName = ref('')
const editingId = ref(null)
const editingName = ref('')

async function fetchCategories() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getCategories(props.boardId)
    if (data.success) {
      categories.value = data.data
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
    const { data } = await boardApi.createCategory(props.boardId, {
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
    const { data } = await boardApi.deleteCategory(props.boardId, categoryId)
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
    const { data } = await boardApi.updateCategory(props.boardId, category.categoryId, {
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
        class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
      />
      <button
        type="submit"
        class="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
      >
        <Plus class="h-4 w-4" />
      </button>
    </form>

    <!-- Category List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <ul v-else class="divide-y divide-gray-200 border border-gray-200 rounded-md">
      <li v-for="category in categories" :key="category.categoryId" class="px-4 py-3 flex items-center justify-between">
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
      <li v-if="categories.length === 0" class="px-4 py-3 text-sm text-gray-500 text-center">
        No categories yet.
      </li>
    </ul>
  </div>
</template>
