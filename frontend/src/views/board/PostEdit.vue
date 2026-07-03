<script setup lang="ts">
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormRouteShell } from '@/features/board/posts/form/usePostFormRouteShell'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const leaveConfirmMessage = t('board.writePost.leaveConfirm')
const { postFormRef, router, boardUrl, postId, handleCancel } = usePostFormRouteShell(leaveConfirmMessage)

function handleSubmitted(result: { boardUrl: string; postId?: string | number }) {
  router.push(`/board/${result.boardUrl}/post/${result.postId ?? postId.value}`)
}

</script>

<template>
  <PostForm
    ref="postFormRef"
    mode="edit"
    :board-url="boardUrl"
    :post-id="postId"
    :on-submitted="handleSubmitted"
    @cancel="handleCancel"
  />
</template>
