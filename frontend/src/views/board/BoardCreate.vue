<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BoardForm from '@/components/board/BoardForm.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { useI18n } from 'vue-i18n'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBoard } from '@/features/board/useBoard'
import { extractErrorMessage } from '@/utils/errorHandler'
import { encodePathSegment } from '@/utils/urlPath'
import type { BoardCreateData } from '@/types'

interface BoardData {
  boardName: string
  boardUrl: string
  description: string
  iconUrl: string
  sortOrder: number
  allowNsfw: boolean
  isPublic: boolean
  agentUseYn: boolean
  guidePrompt: string
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
      const createData: BoardCreateData = {
        boardName: formData.boardName,
        boardUrl: formData.boardUrl,
        description: formData.description,
        iconUrl: formData.iconUrl,
        isPublic: formData.isPublic,
        agentUseYn: formData.agentUseYn,
        guidePrompt: formData.guidePrompt,
      }
      const board = await createBoard(createData)
      router.push(`/board/${encodePathSegment(board.boardUrl)}`)
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
    <PageHeader :title="$t('board.form.createTitle')" size="hero" class="mb-6" />

    <BoardForm :isSubmitting="isSubmitting" :error="error" @submit="handleCreate" @cancel="router.back()" />
  </div>
</template>
