<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { boardApi } from '@/api/board'
import { extractErrorMessage } from '@/utils/errorHandler'
import logger from '@/utils/logger'
import { useI18n } from 'vue-i18n'
import { usePostFormLeaveGuard } from '@/composables/usePostFormLeaveGuard'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'

const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)
const { t } = useI18n()
const router = useRouter()
const leaveConfirmMessage = '페이지에서 나가시겠습니까? 변경사항이 저장되지 않을 수 있습니다.'
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
  const result = await prepareBoardTask.run(({ signal }) => boardApi.ensureInquiryBoard(inquiryBoardUrl.value, { signal }))
  if (result) {
    hasPreparedBoard.value = true
  }
}

usePostFormLeaveGuard(postFormRef, leaveConfirmMessage)

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
    <section class="rounded-lg nv-status-info px-4 py-3">
      <h1 class="text-base font-semibold">
        운영진에게 문의하기
      </h1>
      <p class="mt-1 text-sm">
        문의 글은 운영진이 확인합니다. 필요한 내용을 상세히 작성해 주세요.
      </p>
    </section>

    <div v-if="isPreparingBoard" class="rounded-lg border nv-border nv-surface p-6">
      <div class="flex items-center gap-3 text-sm nv-text-muted">
        <BaseSpinner size="sm" />
        <span>문의 노드를 준비하고 있습니다.</span>
      </div>
    </div>

    <div
      v-else-if="prepareError"
      class="rounded-lg nv-status-danger px-4 py-3 text-sm"
    >
      <p>{{ prepareError }}</p>
      <button type="button" class="mt-2 underline" @click="ensureInquiryBoard">다시 시도</button>
    </div>

    <PostForm
      v-else
      ref="postFormRef"
      mode="create"
      :board-url="inquiryBoardUrl"
      :on-submitted="handleSubmitted"
      create-title-override="문의 작성"
      create-success-toast-message="문의가 성공적으로 등록되었습니다."
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
