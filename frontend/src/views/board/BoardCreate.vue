<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import BoardForm from '@/components/board/BoardForm.vue'
import { useI18n } from 'vue-i18n'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'

const { t } = useI18n()
const router = useRouter()
const { isSubmitting, submit } = useFormSubmit()
const { handleError } = useErrorHandler()

const error = ref('')

async function handleCreate(formData) {
  error.value = ''
  
  await submit(async () => {
    try {
      const { data } = await boardApi.createBoard(formData)
      if (data.success) {
        router.push(`/board/${data.data.boardUrl}`)
      }
    } catch (err) {
      error.value = (err as any).response?.data?.error?.message || t('board.form.createFailed')
      handleError(err, t('board.form.createFailed'))
      throw err
    }
  })
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