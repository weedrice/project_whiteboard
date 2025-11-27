<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import BaseInput from '@/components/common/BaseInput.vue' // BaseInput 추가

const router = useRouter()

const form = ref({
  boardName: '',
  boardUrl: '', // 추가
  description: '',
  iconUrl: '',
  sortOrder: 0,
  allowNsfw: false
})

const isSubmitting = ref(false)
const error = ref('')

async function handleSubmit() {
  if (!form.value.boardName) {
    alert('게시판 이름을 입력해주세요.')
    return
  }
  if (!form.value.boardUrl) {
    alert('게시판 URL을 입력해주세요.')
    return
  }

  isSubmitting.value = true
  error.value = ''

  try {
    const { data } = await boardApi.createBoard(form.value)
    if (data.success) {
      router.push(`/board/${data.data.boardUrl}`) // boardUrl 사용
    }
  } catch (err) {
    console.error('Failed to create board:', err)
    error.value = err.response?.data?.error?.message || '게시판 생성에 실패했습니다.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          게시판 생성
        </h2>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div v-if="error" class="rounded-md bg-red-50 p-4">
        <div class="flex">
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ error }}</h3>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">
        <div class="sm:col-span-6">
          <BaseInput
            label="게시판 이름"
            v-model="form.boardName"
            type="text"
            required
            placeholder="예: 자유게시판, 질문과 답변"
          />
        </div>

        <div class="sm:col-span-6">
          <BaseInput
            label="게시판 URL"
            v-model="form.boardUrl"
            type="text"
            required
            placeholder="예: free, qna (영문 소문자, 숫자, 하이픈만 허용)"
          />
        </div>

        <div class="sm:col-span-6">
          <label for="description" class="block text-sm font-medium text-gray-700">설명</label>
          <div class="mt-1">
            <textarea
              id="description"
              name="description"
              rows="3"
              v-model="form.description"
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border border-gray-300 rounded-md"
              placeholder="게시판에 대한 간략한 설명"
            ></textarea>
          </div>
        </div>

        <div class="sm:col-span-6">
            <BaseInput
                label="아이콘 URL"
                v-model="form.iconUrl"
                type="text"
                placeholder="아이콘 이미지 URL"
            />
        </div>

        <div class="sm:col-span-6">
            <BaseInput
                label="정렬 순서"
                v-model.number="form.sortOrder"
                type="number"
                placeholder="정렬 순서 (숫자)"
            />
        </div>

        <div class="sm:col-span-6">
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
              <label for="allowNsfw" class="font-medium text-gray-700">후방주의 (NSFW) 허용</label>
              <p class="text-gray-500">이 게시판에 부적절한 콘텐츠가 포함될 수 있다면 체크하세요.</p>
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
          취소
        </button>
        <button
          type="submit"
          :disabled="isSubmitting"
          class="ml-3 inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
        >
          {{ isSubmitting ? '생성 중...' : '게시판 생성' }}
        </button>
      </div>
    </form>
  </div>
</template>
