<script setup lang="ts">
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormRouteShell } from '@/features/board/posts/form/usePostFormRouteShell'
import { encodePathSegment } from '@/utils/urlPath'
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'

const { t } = useI18n()
const leaveConfirmMessage = t('board.writePost.leaveConfirm')
const { postFormRef, router, route, boardUrl, postId, handleCancel } = usePostFormRouteShell(leaveConfirmMessage)
const initialDraftId = computed(() => route.query.draftId as string | undefined)

function handleSubmitted(result: { boardUrl: string; postId?: string | number }) {
  router.push(`/board/${encodePathSegment(result.boardUrl)}/post/${encodePathSegment(result.postId ?? postId.value)}`)
}

</script>

<template>
  <PostForm
    ref="postFormRef"
    mode="edit"
    :board-url="boardUrl"
    :post-id="postId"
    :initial-draft-id="initialDraftId"
    :on-submitted="handleSubmitted"
    @cancel="handleCancel"
  />
</template>
