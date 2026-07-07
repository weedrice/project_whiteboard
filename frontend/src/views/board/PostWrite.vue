<script setup lang="ts">
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormRouteShell } from '@/features/board/posts/form/usePostFormRouteShell'
import { encodePathSegment } from '@/utils/urlPath'
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'

const { t } = useI18n()
const leaveConfirmMessage = t('board.writePost.leaveConfirm')
const { postFormRef, router, route, boardUrl, handleCancel } = usePostFormRouteShell(leaveConfirmMessage)
const initialDraftId = computed(() => route.query.draftId as string | undefined)

function handleSubmitted(result: {
  boardUrl: string
  newPostId?: string | number
  isSecret: boolean
  isBoardAdmin: boolean
}) {
  if (!result.newPostId) return
  if (result.isSecret && !result.isBoardAdmin) {
    router.push(`/board/${encodePathSegment(result.boardUrl)}`)
    return
  }
  router.push({
    path: `/board/${encodePathSegment(result.boardUrl)}/post/${encodePathSegment(result.newPostId)}`,
    query: { fromCreate: '1' }
  })
}

</script>

<template>
  <PostForm
    ref="postFormRef"
    mode="create"
    :board-url="boardUrl"
    :initial-draft-id="initialDraftId"
    :on-submitted="handleSubmitted"
    @cancel="handleCancel"
  />
</template>
