<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import CategoryManager from '@/components/board/CategoryManager.vue'

const route = useRoute()
const router = useRouter()
const boardId = route.params.boardId

const form = ref({
  boardName: '',
  description: '',
  allowNsfw: false
})

const isLoading = ref(true)
const isSubmitting = ref(false)

async function fetchBoard() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getBoard(boardId)
    if (data.success) {
      const board = data.data
      form.value = {
        boardName: board.boardName,
        description: board.description,
        allowNsfw: board.allowNsfw || false // Assuming API returns this
      }
    }
  } catch (err) {
    console.error('Failed to load board:', err)
    alert('Failed to load board data.')
    router.push(`/board/${boardId}`)
  } finally {
    isLoading.value = false
  }
}

async function handleSubmit() {
  if (!form.value.boardName) return

  isSubmitting.value = true
  try {
    const { data } = await boardApi.updateBoard(boardId, form.value)
    if (data.success) {
      alert('Board updated successfully.')
      router.push(`/board/${boardId}`)
    }
  } catch (err) {
    console.error('Failed to update board:', err)
    alert('Failed to update board.')
  } finally {
    isSubmitting.value = false
  }
}

async function handleDelete() {
  if (!confirm('Are you sure you want to DELETE this board? This action cannot be undone.')) return

  try {
    const { data } = await boardApi.deleteBoard(boardId)
    if (data.success) {
      alert('Board deleted successfully.')
      router.push('/')
    }
  } catch (err) {
    console.error('Failed to delete board:', err)
    alert('Failed to delete board.')
  }
}

onMounted(fetchBoard)
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div class="md:grid md:grid-cols-3 md:gap-6">
        <div class="md:col-span-1">
          <h3 class="text-lg font-medium leading-6 text-gray-900">Board Settings</h3>
          <p class="mt-1 text-sm text-gray-500">
            Manage board details and categories.
          </p>
        </div>
        <div class="mt-5 md:mt-0 md:col-span-2 space-y-6">
          
          <!-- Board Form -->
          <form @submit.prevent="handleSubmit" class="space-y-6">
            <div>
              <label for="boardName" class="block text-sm font-medium text-gray-700">Board Name</label>
              <div class="mt-1">
                <input
                  type="text"
                  name="boardName"
                  id="boardName"
                  v-model="form.boardName"
                  required
                  class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                />
              </div>
            </div>

            <div>
              <label for="description" class="block text-sm font-medium text-gray-700">Description</label>
              <div class="mt-1">
                <textarea
                  id="description"
                  name="description"
                  rows="3"
                  v-model="form.description"
                  class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border border-gray-300 rounded-md"
                ></textarea>
              </div>
            </div>

            <div class="flex items-start">
              <div class="flex items-center h-5">
                <input
                  id="allowNsfw"
                  name="allowNsfw"
                  type="checkbox"
                  v-model="form.allowNsfw"
                  class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
                />
              </div>
              <div class="ml-3 text-sm">
                <label for="allowNsfw" class="font-medium text-gray-700">Allow NSFW</label>
              </div>
            </div>

            <div class="flex justify-end">
              <button
                type="submit"
                :disabled="isSubmitting"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
              >
                {{ isSubmitting ? 'Saving...' : 'Save Changes' }}
              </button>
            </div>
          </form>

          <hr class="border-gray-200" />

          <!-- Category Manager -->
          <CategoryManager :boardId="boardId" />

          <hr class="border-gray-200" />

          <!-- Danger Zone -->
          <div>
            <h3 class="text-lg font-medium leading-6 text-red-900">Danger Zone</h3>
            <div class="mt-2">
              <button
                type="button"
                @click="handleDelete"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
              >
                Delete Board
              </button>
            </div>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>
