import type { ComputedRef, Ref } from 'vue'
import type { Router } from 'vue-router'
import type { PostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'
import { useEventListener } from '@/composables/useEventListener'
import { isInputFocused } from '@/utils/keyboard'

type PostDetailKeyboardAuthState = {
  isAuthenticated: boolean
}

type UsePostDetailKeyboardShortcutsOptions = {
  router: Router
  authStore: PostDetailKeyboardAuthState
  postView: ComputedRef<PostDetailViewModel | null>
  canEdit: ComputedRef<boolean>
  isReportModalOpen: Ref<boolean>
  scrollToComments: () => void
  buildEditRoute: () => string
  goToList: () => void
  handleBookmark: () => void
  handleShare: () => void
  handleCopyUrl: () => void
  handleLike: () => void
}

export function usePostDetailKeyboardShortcuts({
  router,
  authStore,
  postView,
  canEdit,
  isReportModalOpen,
  scrollToComments,
  buildEditRoute,
  goToList,
  handleBookmark,
  handleShare,
  handleCopyUrl,
  handleLike,
}: UsePostDetailKeyboardShortcutsOptions) {
  const handleKeyDown = (event: KeyboardEvent) => {
    const { key, shiftKey, ctrlKey, altKey, metaKey } = event

    if (ctrlKey || altKey || metaKey) return
    if (isInputFocused()) return
    if (isReportModalOpen.value) return

    if (shiftKey) {
      if (key === 'S') {
        if (authStore.isAuthenticated && postView.value) {
          event.preventDefault()
          handleBookmark()
        }
        return
      }
      if (key === 'Y') {
        event.preventDefault()
        handleShare()
      }
      return
    }

    switch (key) {
      case 'c':
        event.preventDefault()
        scrollToComments()
        break
      case 'u':
        event.preventDefault()
        goToList()
        break
      case 'l':
        if (authStore.isAuthenticated && postView.value) {
          event.preventDefault()
          handleLike()
        }
        break
      case 'y':
        event.preventDefault()
        handleCopyUrl()
        break
      case 'e':
        if (canEdit.value && postView.value) {
          event.preventDefault()
          router.push(buildEditRoute())
        }
        break
      case 'Escape':
        event.preventDefault()
        goToList()
        break
    }
  }

  useEventListener(() => document, 'keydown', handleKeyDown)

  return {
    handleKeyDown,
  }
}
