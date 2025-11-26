<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { boardApi } from '@/api/board'

const route = useRoute()
const router = useRouter()

const boardId = route.params.boardId
const postId = route.params.postId

const categories = ref([])
const isSubmitting = ref(false)
const isLoading = ref(true)

const form = ref({
  title: '',
  content: '',
  categoryId: '',
  tags: '',
  isNsfw: false,
  isSpoiler: false
})

const board = ref(null)

async function fetchData() {
  isLoading.value = true
  try {
    const [postRes, categoriesRes, boardRes] = await Promise.all([
      postApi.getPost(postId),
      boardApi.getCategories(boardId),
      boardApi.getBoard(boardId)
    ])

    if (postRes.data.success) {
      const post = postRes.data.data
      form.value = {
        title: post.title,
        content: post.contents,
        categoryId: post.category?.categoryId || '',
        tags: post.tags ? post.tags.join(', ') : '',
        isNsfw: post.isNsfw,
        isSpoiler: post.isSpoiler
      }
    }

    if (categoriesRes.data.success) {
      categories.value = categoriesRes.data.data
    }

    if (boardRes.data.success) {
      board.value = boardRes.data.data
    }
  } catch (err) {
    console.error('Failed to load data:', err)
    alert('Failed to load post data.')
    router.push(`/board/${boardId}/post/${postId}`)
  } finally {
    isLoading.value = false
  }
}

async function handleSubmit() {
  if (!form.value.title || !form.value.content) {
    alert('Please fill in all required fields.')
    return
  }

  isSubmitting.value = true
  try {
    const payload = {
      ...form.value,
      tags: form.value.tags.split(',').map(tag => tag.trim()).filter(tag => tag),
      contents: form.value.content, // API expects 'contents'
      isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false
    }
    
    // Remove 'content' key as we mapped it to 'contents'
    delete payload.content

    const { data } = await postApi.updatePost(postId, payload)
    if (data.success) {
      router.push(`/board/${boardId}/post/${postId}`)
    }
  } catch (err) {
    console.error('Failed to update post:', err)
    alert('Failed to update post.')
  } finally {
    isSubmitting.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit" class="space-y-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div class="md:grid md:grid-cols-3 md:gap-6">
        <div class="md:col-span-1">
          <h3 class="text-lg font-medium leading-6 text-gray-900">Edit Post</h3>
          <p class="mt-1 text-sm text-gray-500">
            Update your post content.
          </p>
        </div>
        <div class="mt-5 md:mt-0 md:col-span-2 space-y-6">
          
          <!-- Category -->
          <div v-if="categories.length > 0">
            <label for="category" class="block text-sm font-medium text-gray-700">Category</label>
            <select
              id="category"
              v-model="form.categoryId"
              class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
            >
              <option value="" disabled>Select a category</option>
              <option v-for="category in categories" :key="category.categoryId" :value="category.categoryId">
                {{ category.name }}
              </option>
            </select>
          </div>

          <!-- Title -->
          <div>
            <label for="title" class="block text-sm font-medium text-gray-700">Title</label>
            <div class="mt-1">
              <input
                type="text"
                name="title"
                id="title"
                v-model="form.title"
                required
                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
              />
            </div>
          </div>

          <!-- Content -->
          <div>
            <label for="content" class="block text-sm font-medium text-gray-700">Content</label>
            <div class="mt-1">
              <textarea
                id="content"
                name="content"
                rows="10"
                v-model="form.content"
                required
                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border border-gray-300 rounded-md"
              ></textarea>
            </div>
          </div>

          <!-- Tags -->
          <div>
            <label for="tags" class="block text-sm font-medium text-gray-700">Tags</label>
            <div class="mt-1">
              <input
                type="text"
                name="tags"
                id="tags"
                v-model="form.tags"
                placeholder="Comma separated tags (e.g. vue, javascript)"
                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
              />
            </div>
          </div>

          <!-- Options -->
          <div class="flex items-start space-x-4">
            <div v-if="board?.allowNsfw" class="flex items-center h-5">
              <input
                id="nsfw"
                name="nsfw"
                type="checkbox"
                v-model="form.isNsfw"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
              />
              <label for="nsfw" class="ml-2 block text-sm text-gray-900">NSFW</label>
            </div>
            <div class="flex items-center h-5">
              <input
                id="spoiler"
                name="spoiler"
                type="checkbox"
                v-model="form.isSpoiler"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
              />
              <label for="spoiler" class="ml-2 block text-sm text-gray-900">Spoiler</label>
            </div>
          </div>

        </div>
      </div>

      <div class="flex justify-end">
        <button
          type="button"
          @click="router.back()"
          class="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
        >
          Cancel
        </button>
        <button
          type="submit"
          :disabled="isSubmitting"
          class="ml-3 inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
        >
          {{ isSubmitting ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </form>
  </div>
</template>
