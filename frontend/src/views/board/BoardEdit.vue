<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import CategoryManager from '@/components/board/CategoryManager.vue'
import BoardForm from '@/components/board/BoardForm.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const boardUrl = route.params.boardUrl

const form = ref({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  sortOrder: 0,
  allowNsfw: false
})

const isLoading = ref(true)
const isSubmitting = ref(false)
const error = ref('')

async function fetchBoard() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getBoard(boardUrl)
    if (data.success) {
      const board = data.data
      form.value = {
        boardName: board.boardName,
        boardUrl: board.boardUrl,
        description: board.description,
        iconUrl: board.iconUrl || '',
        sortOrder: board.sortOrder || 0,
        allowNsfw: board.allowNsfw || false
      }
    }
  } catch (err) {
    logger.error('Failed to load board:', err)
    alert(t('board.writePost.failLoad'))
    router.push(`/board/${boardUrl}`)
  } finally {
    isLoading.value = false
  }
}

async function handleUpdate(formData) {
  isSubmitting.value = true
  error.value = ''
  try {
    const { data } = await boardApi.updateBoard(boardUrl, formData)
    if (data.success) {
      alert(t('board.form.successUpdate'))
      router.push(`/board/${data.data.boardUrl}`)
    }
  } catch (err) {
    logger.error('Failed to update board:', err)
    error.value = t('board.form.failUpdate')
  } finally {
    isSubmitting.value = false
  }
}

async function handleDelete() {
  if (!confirm(t('board.form.deleteConfirm'))) return

  try {
    const { data } = await boardApi.deleteBoard(boardUrl)
    if (data.success) {
      alert(t('board.form.successDelete'))
      router.push('/')
    }
  } catch (err) {
    logger.error('Failed to delete board:', err)
    alert(t('board.form.failDelete'))
  }
}

onMounted(fetchBoard)
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 shadow sm:rounded-lg overflow-hidden">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
        <div>
          <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('board.form.editTitle') }}</h3>
          <p class="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">{{ $t('board.form.editDesc') }}</p>
        </div>
        <button
          type="button"
          @click="router.back()"
          class="bg-white dark:bg-gray-700 py-2 px-4 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
        >
          {{ $t('common.back') }}
        </button>
      </div>

      <div class="px-4 py-5 sm:p-6 space-y-6">
          <!-- Board Form -->
          <BoardForm
            :initialData="form"
            :isEdit="true"
            :isSubmitting="isSubmitting"
            :error="error"
            @submit="handleUpdate"
            @cancel="router.back()"
          />

          <hr class="border-gray-200 dark:border-gray-700" />

          <!-- Category Manager -->
          <div class="py-6">
            <CategoryManager :boardUrl="boardUrl" />
          </div>

          <hr class="border-gray-200 dark:border-gray-700" />

          <!-- Delete Board (Moved to bottom right) -->
          <div class="flex justify-end">
              <button
                type="button"
                @click="handleDelete"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
              >
                {{ $t('board.form.delete') }}
              </button>
          </div>
      </div>
    </div>
  </div>
</template>