<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormLeaveGuard } from '@/composables/usePostFormLeaveGuard'

const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)
const route = useRoute()
const router = useRouter()
const boardUrl = computed(() => String(route.params.boardUrl ?? ''))
const leaveConfirmMessage = '페이지에서 나가시겠습니까? 변경사항이 저장되지 않을 수 있습니다.'

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
