<script setup lang="ts">
import { ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'

const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)

onBeforeRouteLeave((_to, _from, next) => {
  const form = postFormRef.value
  if (form?.hasUnsavedChanges?.()) {
    const message = form.getLeaveConfirmMessage?.() ?? '사이트에서 나가시겠습니까? 변경사항이 저장되지 않을 수 있습니다.'
    if (!window.confirm(message)) {
      next(false)
      return
    }
  }
  next()
})
</script>

<template>
  <PostForm ref="postFormRef" mode="create" />
</template>
