<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormLeaveGuard } from '@/composables/usePostFormLeaveGuard'

const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)
const route = useRoute()
const router = useRouter()
const boardUrl = computed(() => String(route.params.boardUrl ?? ''))
const postId = computed(() => String(route.params.postId ?? ''))
const leaveConfirmMessage = '페이지에서 나가시겠습니까? 변경사항이 저장되지 않을 수 있습니다.'

usePostFormLeaveGuard(postFormRef, leaveConfirmMessage)

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
  />
</template>
