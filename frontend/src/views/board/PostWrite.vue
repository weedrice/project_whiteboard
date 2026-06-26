<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormLeaveGuard } from '@/composables/usePostFormLeaveGuard'
import { useI18n } from 'vue-i18n'

const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const boardUrl = computed(() => String(route.params.boardUrl ?? ''))
const leaveConfirmMessage = t('board.writePost.leaveConfirm')

usePostFormLeaveGuard(postFormRef, leaveConfirmMessage)

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

function handleCancel() {
  router.back()
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
