<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import { postApi } from '@/api/post'
import PostTags from '@/components/tag/PostTags.vue'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const boardUrl = route.params.boardUrl // boardId 대신 boardUrl 사용

const categories = ref([])
const isLoading = ref(false)
const isSubmitting = ref(false)
const error = ref('')

const form = ref({
  categoryId: '',
  title: '',
  contents: '',
  tags: [], // Array of strings
  isNsfw: false,
  isSpoiler: false,
  isNotice: false
})

const board = ref(null)

onMounted(async () => {
  isLoading.value = true
  try {
    const [categoriesRes, boardRes] = await Promise.all([
      boardApi.getCategories(boardUrl), // boardUrl 사용
      boardApi.getBoard(boardUrl) // boardUrl 사용
    ])

    if (categoriesRes.data.success) {
      categories.value = categoriesRes.data.data
      if (categories.value.length > 0) {
        form.value.categoryId = categories.value[0].categoryId
      }
    }

    if (boardRes.data.success) {
      board.value = boardRes.data.data
    }
  } catch (err) {
    console.error('Failed to load data:', err)
    error.value = 'Failed to load board data.'
  } finally {
    isLoading.value = false
  }
})

async function handleSubmit() {
  if (!form.value.title || !form.value.contents || !form.value.categoryId) {
    alert('Please fill in all required fields.')
    return
  }

  isSubmitting.value = true
  error.value = ''

  try {
    const payload = {
      categoryId: form.value.categoryId,
      title: form.value.title,
      contents: form.value.contents,
      tags: form.value.tags,
      isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false,
      isSpoiler: form.value.isSpoiler,
      isNotice: form.value.isNotice,
      fileIds: [] // File upload not implemented yet
    }

    const { data } = await postApi.createPost(boardUrl, payload) // boardUrl 사용
    if (data.success) {
      router.push(`/board/${board.value.boardUrl}`) // boardUrl 사용
    }
  } catch (err) {
    console.error('Failed to create post:', err)
    error.value = err.response?.data?.error?.message || 'Failed to create post.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="w-full">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          새 글 작성
        </h2>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit" class="space-y-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div v-if="error" class="rounded-md bg-red-50 p-4">
        <div class="flex">
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ error }}</h3>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">
        <div class="sm:col-span-3">
          <label for="category" class="block text-sm font-medium text-gray-700">카테고리</label>
          <div class="mt-1">
            <select
              id="category"
              v-model="form.categoryId"
              name="category"
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
            >
              <option v-for="category in categories" :key="category.categoryId" :value="category.categoryId">
                {{ category.name }}
              </option>
            </select>
          </div>
        </div>

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
              placeholder="제목을 입력하세요"
            />
          </div>
        </div>

        <div class="sm:col-span-6">
          <label for="contents" class="block text-sm font-medium text-gray-700">내용</label>
          <div class="mt-1">
            <textarea
              id="contents"
              name="contents"
              rows="10"
              v-model="form.contents"
              required
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border border-gray-300 rounded-md"
              placeholder="내용을 입력하세요..."
            ></textarea>
          </div>
        </div>

        <div class="sm:col-span-6">
          <label for="tags" class="block text-sm font-medium text-gray-700">태그</label>
          <div class="mt-1">
            <PostTags v-model="form.tags" />
          </div>
        </div>

        <div class="sm:col-span-6">
          <div v-if="board?.isAdmin" class="flex items-start mb-4">
            <div class="flex items-center h-5">
              <input
                id="isNotice"
                name="isNotice"
                type="checkbox"
                v-model="form.isNotice"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="isNotice" class="font-medium text-gray-700 cursor-pointer">공지사항</label>
              <p class="text-gray-500">이 글을 공지사항으로 등록합니다.</p>
            </div>
          </div>

          <div v-if="board?.allowNsfw" class="flex items-start">
            <div class="flex items-center h-5">
              <input
                id="isNsfw"
                name="isNsfw"
                type="checkbox"
                v-model="form.isNsfw"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="isNsfw" class="font-medium text-gray-700 cursor-pointer">후방주의 (NSFW)</label>
              <p class="text-gray-500">직장에서 보기에 부적절한 콘텐츠를 포함합니다.</p>
            </div>
          </div>
          <div class="flex items-start mt-4">
            <div class="flex items-center h-5">
              <input
                id="isSpoiler"
                name="isSpoiler"
                type="checkbox"
                v-model="form.isSpoiler"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="isSpoiler" class="font-medium text-gray-700 cursor-pointer">스포일러</label>
              <p class="text-gray-500">스포일러를 포함합니다.</p>
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
          {{ isSubmitting ? '등록 중...' : '등록' }}
        </button>
      </div>
    </form>
  </div>
</template>
