<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BoardForm from '@/components/board/BoardForm.vue'
import { useI18n } from 'vue-i18n'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBoard } from '@/composables/useBoard'
import { extractErrorMessage } from '@/utils/errorHandler'
import type { BoardCreateData } from '@/types'

interface BoardData {
  boardName: string
  boardUrl: string
  description: string
  iconUrl: string
  sortOrder: number
  allowNsfw: boolean
  isPublic: boolean
}

const { t } = useI18n()
const router = useRouter()
const { isSubmitting, submit } = useFormSubmit()
const { handleError } = useErrorHandler()
const { useCreateBoard } = useBoard()
const { mutateAsync: createBoard } = useCreateBoard()

const error = ref('')

async function handleCreate(formData: BoardData) {
  error.value = ''

  await submit(async () => {
    try {
      const board = await createBoard(formData as BoardCreateData)
      router.push(`/board/${board.boardUrl}`)
    } catch (err: unknown) {
      error.value = extractErrorMessage(err) || t('board.form.createFailed')
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

    <BoardForm :isSubmitting="isSubmitting" :error="error" @submit="handleCreate" @cancel="router.back()" />
  </div>
</template>
