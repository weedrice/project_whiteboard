<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import BoardForm from '@/components/board/BoardForm.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

const { t } = useI18n()
const router = useRouter()

const isSubmitting = ref(false)
const error = ref('')

async function handleCreate(formData) {
  isSubmitting.value = true
  error.value = ''

  try {
    const { data } = await boardApi.createBoard(formData)
    if (data.success) {
      router.push(`/board/${data.data.boardUrl}`)
    }
  } catch (err) {
    logger.error('Failed to create board:', err)
    error.value = err.response?.data?.error?.message || t('board.form.failCreate')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-9 text-gray-900 dark:text-white sm:text-3xl pb-1">
          {{ $t('board.form.createTitle') }}
        </h2>
      </div>
    </div>

    <BoardForm
      :isSubmitting="isSubmitting"
      :error="error"
      @submit="handleCreate"
      @cancel="router.back()"
    />
  </div>
</template>