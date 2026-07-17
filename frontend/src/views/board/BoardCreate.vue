<script setup lang="ts">
import { onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BoardForm from '@/components/board/BoardForm.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { useI18n } from 'vue-i18n'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBoard } from '@/features/board/useBoard'
import { extractErrorMessage } from '@/utils/errorHandler'
import { encodePathSegment } from '@/utils/urlPath'
import type { BoardCreateData } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { isCancellationError } from '@/utils/cancellationError'

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
const route = useRoute()
const authStore = useAuthStore()
const { isSubmitting, submit } = useFormSubmit()
const { handleError } = useErrorHandler()
const { useCreateBoard } = useBoard()
const { mutateAsync: createBoard } = useCreateBoard()

const error = ref('')
let createController: AbortController | null = null

async function handleCreate(formData: BoardData) {
  error.value = ''
  const intent = captureAuthSessionIntent(authStore)
  const routeIntent = route.fullPath

  await submit(async () => {
    createController?.abort()
    const controller = new AbortController()
    createController = controller
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
      const board = await createBoard({ ...createData, signal: controller.signal })
      if (
        controller.signal.aborted
        || !isAuthSessionIntentCurrent(authStore, intent)
        || route.fullPath !== routeIntent
      ) return
      await router.push(`/board/${encodePathSegment(board.boardUrl)}`)
    } catch (err: unknown) {
      if (controller.signal.aborted || !isAuthSessionIntentCurrent(authStore, intent) || isCancellationError(err)) return
      error.value = extractErrorMessage(err) || t('board.form.createFailed')
      handleError(err, t('board.form.createFailed'))
      throw err
    } finally {
      if (createController === controller) createController = null
    }
  })
}

watch(() => authStore.sessionGeneration, () => createController?.abort())
onScopeDispose(() => createController?.abort())
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <PageHeader :title="$t('board.form.createTitle')" size="hero" class="mb-6" />

    <BoardForm :isSubmitting="isSubmitting" :error="error" @submit="handleCreate" @cancel="router.back()" />
  </div>
</template>
