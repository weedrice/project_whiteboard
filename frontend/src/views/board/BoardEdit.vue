<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import CategoryManager from '@/components/board/CategoryManager.vue'
import BoardForm from '@/components/board/BoardForm.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()

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
    toastStore.addToast(t('board.writePost.failLoad'), 'error')
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
      toastStore.addToast(t('board.form.successUpdate'), 'success')
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
  const isConfirmed = await confirm(t('board.form.deleteConfirm'))
  if (!isConfirmed) return

  try {
    const { data } = await boardApi.deleteBoard(boardUrl)
    if (data.success) {
      toastStore.addToast(t('board.form.successDelete'), 'success')
      router.push('/')
    }
  } catch (err) {
    logger.error('Failed to delete board:', err)
    toastStore.addToast(t('board.form.failDelete'), 'error')
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
        <BaseButton type="button" @click="router.back()" variant="secondary">
          {{ $t('common.back') }}
        </BaseButton>
      </div>

      <div class="px-4 py-5 sm:p-6 space-y-6">
        <!-- Board Form -->
        <BoardForm :initialData="form" :isEdit="true" :isSubmitting="isSubmitting" :error="error" @submit="handleUpdate"
          @cancel="router.back()" />

        <hr class="border-gray-200 dark:border-gray-700" />

        <!-- Category Manager -->
        <div class="py-6">
          <CategoryManager :boardUrl="boardUrl" />
        </div>

        <hr class="border-gray-200 dark:border-gray-700" />

        <!-- Delete Board (Moved to bottom right) -->
        <div class="flex justify-end">
          <BaseButton type="button" @click="handleDelete" variant="danger">
            {{ $t('board.form.delete') }}
          </BaseButton>
        </div>
      </div>
    </div>
  </div>
</template>