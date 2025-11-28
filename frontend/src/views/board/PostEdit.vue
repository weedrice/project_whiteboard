<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { boardApi } from '@/api/board'

const route = useRoute()
const router = useRouter()

const boardUrl = route.params.boardUrl // boardId 대신 boardUrl 사용
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
      boardApi.getCategories(boardUrl), // boardUrl 사용
      boardApi.getBoard(boardUrl) // boardUrl 사용
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
    router.push(`/board/${boardUrl}/post/${postId}`) // boardUrl 사용
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
      isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false,
      isSpoiler: form.value.isSpoiler // Explicitly include isSpoiler
    }
    
    // Remove 'content' key as we mapped it to 'contents'
    delete payload.content

    const { data } = await postApi.updatePost(postId, payload)
    if (data.success) {
      router.push(`/board/${board.value.boardUrl}/post/${postId}`) // boardUrl 사용
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
  <div class="w-full">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          게시글 수정
        </h2>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit" class="space-y-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div class="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">
        
        <!-- Category -->
        <div class="sm:col-span-3" v-if="categories.length > 0">
          <label for="category" class="block text-sm font-medium text-gray-700">카테고리</label>
          <div class="mt-1">
            <select
              id="category"
              v-model="form.categoryId"
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
            >
              <option value="" disabled>카테고리 선택</option>
              <option v-for="category in categories" :key="category.categoryId" :value="category.categoryId">
                {{ category.name }}
              </option>
            </select>
          </div>
        </div>

        <!-- Title -->
        <div class="sm:col-span-6">
          <label for="title" class="block text-sm font-medium text-gray-700">제목</label>
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
        <div class="sm:col-span-6">
          <label for="content" class="block text-sm font-medium text-gray-700">내용</label>
          <div class="mt-1 h-96">
            <QuillEditor
              theme="snow"
              toolbar="full"
              contentType="html"
              v-model:content="form.content"
            />
          </div>
        </div>

        <!-- Tags -->
        <div class="sm:col-span-6 mt-12">
          <label for="tags" class="block text-sm font-medium text-gray-700">태그</label>
          <div class="mt-1">
            <input
              type="text"
              name="tags"
              id="tags"
              v-model="form.tags"
              placeholder="쉼표로 구분된 태그 (예: vue, javascript)"
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
            />
          </div>
        </div>

        <!-- Options -->
        <div class="sm:col-span-6">
          <div v-if="board?.allowNsfw" class="flex items-start mb-4">
            <div class="flex items-center h-5">
              <input
                id="nsfw"
                name="nsfw"
                type="checkbox"
                v-model="form.isNsfw"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="nsfw" class="font-medium text-gray-700 cursor-pointer">후방주의 (NSFW)</label>
            </div>
          </div>
          <div class="flex items-start">
            <div class="flex items-center h-5">
              <input
                id="spoiler"
                name="spoiler"
                type="checkbox"
                v-model="form.isSpoiler"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="spoiler" class="font-medium text-gray-700 cursor-pointer">스포일러</label>
            </div>
          </div>
        </div>

      </div>

      <div class="flex justify-end">
        <button
          type="button"
          @click="router.back()"
          class="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
        >
          취소
        </button>
        <button
          type="submit"
          :disabled="isSubmitting"
          class="ml-3 inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 cursor-pointer"
        >
          {{ isSubmitting ? '저장 중...' : '수정 완료' }}
        </button>
      </div>
    </form>
  </div>
</template>
