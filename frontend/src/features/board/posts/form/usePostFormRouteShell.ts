import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PostForm from '@/components/board/PostForm.vue'
import { usePostFormLeaveGuard } from '@/features/board/posts/form/usePostFormLeaveGuard'

export function usePostFormRouteShell(leaveConfirmMessage: string) {
  const postFormRef = ref<InstanceType<typeof PostForm> | null>(null)
  const route = useRoute()
  const router = useRouter()
  const boardUrl = computed(() => String(route.params.boardUrl ?? ''))
  const postId = computed(() => String(route.params.postId ?? ''))

  usePostFormLeaveGuard(postFormRef, leaveConfirmMessage)

  function handleCancel() {
    router.back()
  }

  return {
    postFormRef,
    route,
    router,
    boardUrl,
    postId,
    handleCancel,
  }
}
