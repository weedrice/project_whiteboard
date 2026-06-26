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
const postId = computed(() => String(route.params.postId ?? ''))
const leaveConfirmMessage = t('board.writePost.leaveConfirm')

usePostFormLeaveGuard(postFormRef, leaveConfirmMessage)

function handleSubmitted(result: { boardUrl: string; postId?: string | number }) {
  router.push(`/board/${result.boardUrl}/post/${result.postId ?? postId.value}`)
}

function handleCancel() {
  router.back()
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
