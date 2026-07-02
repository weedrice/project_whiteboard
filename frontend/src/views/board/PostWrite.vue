<script setup lang="ts">
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormRouteShell } from '@/composables/usePostFormRouteShell'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const leaveConfirmMessage = t('board.writePost.leaveConfirm')
const { postFormRef, router, boardUrl, handleCancel } = usePostFormRouteShell(leaveConfirmMessage)

function handleSubmitted(result: {
  boardUrl: string
  newPostId?: string | number
  isSecret: boolean
  isBoardAdmin: boolean
}) {
  if (!result.newPostId) return
  if (result.isSecret && !result.isBoardAdmin) {
    router.push(`/board/${result.boardUrl}`)
    return
  }
  router.push({
    path: `/board/${result.boardUrl}/post/${result.newPostId}`,
    query: { fromCreate: '1' }
  })
}

</script>

<template>
  <PostForm
    ref="postFormRef"
    mode="create"
    :board-url="boardUrl"
    :on-submitted="handleSubmitted"
    @cancel="handleCancel"
  />
</template>
