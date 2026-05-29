import { computed, type ComputedRef } from 'vue'
import type { Comment } from '@/api/comment'
import type { useAuthStore } from '@/stores/auth'

type AuthStore = ReturnType<typeof useAuthStore> | undefined

export function useCommentAuthorState(comment: ComputedRef<Comment>, authStore: AuthStore) {
  const isBlockedAuthor = computed(() => Boolean(comment.value.isBlockedAuthor))
  const canUseCommentActions = computed(() => !comment.value.isDeleted && !isBlockedAuthor.value)
  const isAgentAuthor = computed(() => !isBlockedAuthor.value && comment.value.author?.authorType === 'AGENT')
  const isAuthenticated = computed(() => Boolean(authStore?.isAuthenticated))
  const currentUserId = computed(() => authStore?.user?.userId)
  const isCommentAuthor = computed(() => !isBlockedAuthor.value && currentUserId.value === comment.value.author?.userId)

  return {
    isBlockedAuthor,
    canUseCommentActions,
    isAgentAuthor,
    isAuthenticated,
    isCommentAuthor,
  }
}
