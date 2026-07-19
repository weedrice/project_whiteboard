<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormRouteShell } from '@/features/board/posts/form/usePostFormRouteShell'
import { usePost } from '@/features/board/posts/queries/usePost'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'

const { t } = useI18n()
const { postFormRef, router, route, handleCancel } = usePostFormRouteShell(
  t('board.writePost.leaveConfirm'),
)
const scheduledPostId = computed(() => String(route.params.scheduledPostId ?? ''))
const { useScheduledPostDetail } = usePost()
const { data: scheduledPost, isLoading, isError, refetch } = useScheduledPostDetail(
  scheduledPostId,
  computed(() => Boolean(scheduledPostId.value)),
)

function handleSubmitted() {
  router.push('/mypage/drafts')
}
</script>

<template>
  <PostForm
    v-if="scheduledPost"
    ref="postFormRef"
    mode="edit"
    :board-url="scheduledPost.boardUrl"
    :scheduled-post-id="scheduledPostId"
    :on-submitted="handleSubmitted"
    @cancel="handleCancel"
  />
  <div v-else-if="isLoading" class="py-10 text-center">
    <BaseSpinner size="lg" />
  </div>
  <ErrorState
    v-else-if="isError"
    :message="t('board.writePost.loadFailed')"
    show-retry
    @retry="refetch"
  />
</template>
