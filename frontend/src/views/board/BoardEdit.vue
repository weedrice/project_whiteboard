<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import CategoryManager from '@/components/board/CategoryManager.vue'
import BaseInput from '@/components/common/BaseInput.vue' // BaseInput 추가

const route = useRoute()
const router = useRouter()
const boardUrl = route.params.boardUrl // boardId 대신 boardUrl 사용

const form = ref({
  boardName: '',
  description: '',
  iconUrl: '', // 추가
  sortOrder: 0, // 추가
  allowNsfw: false
})

const isLoading = ref(true)
const isSubmitting = ref(false)

async function fetchBoard() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getBoard(boardUrl) // boardUrl 사용
    if (data.success) {
      const board = data.data
      form.value = {
        boardName: board.boardName,
        description: board.description,
        iconUrl: board.iconUrl || '', // 추가
        sortOrder: board.sortOrder || 0, // 추가
        allowNsfw: board.allowNsfw || false
      }
    }
  } catch (err) {
    console.error('Failed to load board:', err)
    alert('Failed to load board data.')
    router.push(`/board/${boardUrl}`) // boardUrl 사용
  } finally {
    isLoading.value = false
  }
}

async function handleSubmit() {
  if (!form.value.boardName) return

  isSubmitting.value = true
  try {
    const { data } = await boardApi.updateBoard(boardUrl, form.value) // boardUrl 사용
    if (data.success) {
      alert('게시판이 수정되었습니다.')
      router.push(`/board/${data.data.boardUrl}`) // boardUrl 사용
    }
  } catch (err) {
    console.error('Failed to update board:', err)
    alert('게시판 수정에 실패했습니다.')
  } finally {
    isSubmitting.value = false
  }
}

async function handleDelete() {
  if (!confirm('정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return

  try {
    const { data } = await boardApi.deleteBoard(boardUrl) // boardUrl 사용
    if (data.success) {
      alert('게시판이 삭제되었습니다.')
      router.push('/')
    }
  } catch (err) {
    console.error('Failed to delete board:', err)
    alert('게시판 삭제에 실패했습니다.')
  }
}

onMounted(fetchBoard)
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white shadow sm:rounded-lg overflow-hidden">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 flex justify-between items-center">
        <div>
          <h3 class="text-lg leading-6 font-medium text-gray-900">게시판 설정</h3>
          <p class="mt-1 max-w-2xl text-sm text-gray-500">게시판 정보와 카테고리를 관리합니다.</p>
        </div>
        <button
          type="button"
          @click="router.back()"
          class="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
        >
          뒤로 가기
        </button>
      </div>

      <div class="px-4 py-5 sm:p-6 space-y-6">
          <!-- Board Form -->
          <form @submit.prevent="handleSubmit" class="space-y-6">
            <div>
              <label for="boardName" class="block text-sm font-medium text-gray-700">게시판 이름</label>
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
              <label for="description" class="block text-sm font-medium text-gray-700">설명</label>
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

            <BaseInput
              label="아이콘 URL"
              v-model="form.iconUrl"
              type="text"
              placeholder="아이콘 이미지 URL"
            />

            <BaseInput
              label="정렬 순서"
              v-model.number="form.sortOrder"
              type="number"
              placeholder="정렬 순서 (숫자)"
            />

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
              </div>
            </div>

            <div class="flex justify-end">
              <button
                type="submit"
                :disabled="isSubmitting"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
              >
                {{ isSubmitting ? '저장 중...' : '변경사항 저장' }}
              </button>
            </div>
          </form>

          <hr class="border-gray-200" />

          <!-- Category Manager -->
          <CategoryManager :boardUrl="boardUrl" />

          <hr class="border-gray-200" />

          <!-- Danger Zone -->
          <div>
            <h3 class="text-lg font-medium leading-6 text-red-900">위험 구역</h3>
            <div class="mt-2">
              <button
                type="button"
                @click="handleDelete"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
              >
                게시판 삭제
              </button>
            </div>
          </div>
      </div>
    </div>
  </div>
</template>
