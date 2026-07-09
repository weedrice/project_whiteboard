import { computed, type Ref } from 'vue'
import type { Post } from '@/types'

interface PostDetailPermissionInputs {
  currentUserId: Ref<number | undefined>
  isAuthenticated: Ref<boolean>
  isAdmin: Ref<boolean>
}

export function usePostDetailPermissions(
  post: Ref<Post | undefined>,
  inputs: PostDetailPermissionInputs
) {
  const isAuthor = computed(() => (
    !!inputs.currentUserId.value
    && !!post.value
    && inputs.currentUserId.value === post.value.author.userId
  ))

  const isAgentAuthor = computed(() => post.value?.author?.authorType === 'AGENT')
  const canEdit = computed(() => isAuthor.value && !!post.value)
  const canDelete = computed(() => !!post.value && (
    isAuthor.value
    || inputs.isAdmin.value
    || !!post.value.board.isAdmin
  ))
  const canManage = computed(() => !!post.value && (
    inputs.isAdmin.value
    || !!post.value.board.isAdmin
  ))
  const canReport = computed(() => inputs.isAuthenticated.value && !isAuthor.value)

  return {
    isAuthor,
    isAgentAuthor,
    canEdit,
    canDelete,
    canManage,
    canReport
  }
}
