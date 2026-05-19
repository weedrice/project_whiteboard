<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { boardApi } from '@/api/board'
import { extractErrorMessage } from '@/utils/errorHandler'
import logger from '@/utils/logger'
import { useI18n } from 'vue-i18n'
import { usePostFormLeaveGuard } from '@/composables/usePostFormLeaveGuard'

const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)
const isPreparingBoard = ref(true)
const prepareError = ref('')
const { t } = useI18n()
const router = useRouter()
const leaveConfirmMessage = '페이지에서 나가시겠습니까? 변경사항이 저장되지 않을 수 있습니다.'

const inquiryBoardUrl = computed(() => {
  const fromEnv = (import.meta.env.VITE_INQUIRY_BOARD_URL || 'inquiry').trim()
  return fromEnv || 'inquiry'
})

const ensureInquiryBoard = async () => {
  isPreparingBoard.value = true
  prepareError.value = ''
  try {
    await boardApi.ensureInquiryBoard(inquiryBoardUrl.value)
  } catch (error) {
    logger.error('Failed to ensure inquiry board:', error)
    prepareError.value = extractErrorMessage(error) || t('board.loadFailed')
  } finally {
    isPreparingBoard.value = false
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
</script>

<template>
  <div class="space-y-4">
    <section class="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 dark:border-blue-900 dark:bg-blue-950/30">
      <h1 class="text-base font-semibold text-blue-900 dark:text-blue-100">
        운영진에게 문의하기
      </h1>
      <p class="mt-1 text-sm text-blue-800 dark:text-blue-200">
        문의 글은 운영진이 확인합니다. 필요한 내용을 상세히 작성해 주세요.
      </p>
    </section>

    <div v-if="isPreparingBoard" class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
      <div class="flex items-center gap-3 text-sm text-gray-600 dark:text-gray-300">
        <BaseSpinner size="sm" />
        <span>문의 게시판을 준비하고 있습니다.</span>
      </div>
    </div>

    <div
      v-else-if="prepareError"
      class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300"
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
    />
  </div>
</template>
