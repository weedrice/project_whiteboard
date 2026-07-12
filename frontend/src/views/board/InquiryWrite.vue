<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import PostForm from '@/components/board/PostForm.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { boardApi } from '@/api/board'
import { extractErrorMessage } from '@/utils/errorHandler'
import logger from '@/utils/logger'
import { useI18n } from 'vue-i18n'
import { usePostFormRouteShell } from '@/features/board/posts/form/usePostFormRouteShell'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'

const { t } = useI18n()
const leaveConfirmMessage = t('board.inquiryWrite.leaveConfirm')
const { postFormRef, router } = usePostFormRouteShell(leaveConfirmMessage)
const hasPreparedBoard = ref(false)

const inquiryBoardUrl = computed(() => {
  const fromEnv = (import.meta.env.VITE_INQUIRY_BOARD_URL || 'inquiry').trim()
  return fromEnv || 'inquiry'
})

const prepareBoardTask = useLatestAsyncTask<string>({
  getErrorValue: (error) => extractErrorMessage(error) || t('board.loadFailed'),
  onError: (error) => {
    logger.error('Failed to ensure inquiry board:', error)
  },
})
const isPreparingBoard = computed(() => prepareBoardTask.loading.value || (!hasPreparedBoard.value && !prepareBoardTask.error.value))
const prepareError = computed(() => prepareBoardTask.error.value || '')

const ensureInquiryBoard = async () => {
  hasPreparedBoard.value = false
  const result = await prepareBoardTask.run(({ signal }) => boardApi.ensureInquiryBoard(inquiryBoardUrl.value, {
    signal,
    skipGlobalErrorHandler: true,
  }))
  if (result) {
    hasPreparedBoard.value = true
  }
}

function handleSubmitted() {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    router.back()
    return
  }
  router.push('/')
}

onMounted(() => {
  ensureInquiryBoard()
})

onUnmounted(() => {
  prepareBoardTask.reset()
})
</script>

<template>
  <div class="space-y-4">
    <PageHeader
      :title="t('board.inquiryWrite.title')"
      :description="t('board.inquiryWrite.description')"
      size="compact"
      class="rounded-lg nv-status-info px-4 py-3"
    />

    <div v-if="isPreparingBoard" class="rounded-lg border nv-border nv-surface p-6">
      <div class="flex items-center gap-3 text-sm nv-text-muted">
        <BaseSpinner size="sm" />
        <span>{{ t('board.inquiryWrite.preparing') }}</span>
      </div>
    </div>

    <div
      v-else-if="prepareError"
      class="rounded-lg nv-status-danger px-4 py-3 text-sm"
    >
      <p>{{ prepareError }}</p>
      <BaseButton type="button" variant="ghost" size="sm" class="mt-2" @click="ensureInquiryBoard">
        {{ t('common.error.retry') }}
      </BaseButton>
    </div>

    <PostForm
      v-else
      ref="postFormRef"
      mode="create"
      :board-url="inquiryBoardUrl"
      :on-submitted="handleSubmitted"
      :create-title-override="t('board.inquiryWrite.createTitle')"
      :create-success-toast-message="t('board.inquiryWrite.createSuccess')"
      :hide-category="true"
      :hide-tags="true"
      :hide-notice="true"
      :hide-spoiler="true"
      :hide-secret="true"
      :skip-board-lookup="true"
      :hide-board-label="true"
      :hide-preview="true"
      @cancel="handleSubmitted"
    />
  </div>
</template>
