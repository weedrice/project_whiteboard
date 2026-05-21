import { onUnmounted, ref, type ComputedRef, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router, RouteLocationRaw } from 'vue-router'
import type { Post } from '@/types'
import logger from '@/utils/logger'

interface ToastStoreLike {
  addToast: (message: string, type?: 'info' | 'success' | 'error' | 'warning') => void
}

type PostId = string | number
type MutationFn<T = PostId> = (
  payload: T,
  options?: {
    onSuccess?: () => void
    onError?: (err: unknown) => void
  }
) => void

interface ReportPayload {
  targetPostId: PostId
  reason: string
}

interface UsePostDetailActionsOptions {
  post: Ref<Post | null | undefined>
  canReport: ComputedRef<boolean>
  authStore: { isAuthenticated: boolean }
  route: RouteLocationNormalizedLoaded
  router: Router
  toastStore: ToastStoreLike
  confirm: (message: string) => Promise<boolean>
  t: (key: string) => string
  buildBoardListRoute: (boardUrl: string) => RouteLocationRaw
  closeOverflowMenu: () => void
  deleteMutate: MutationFn
  likeMutate: MutationFn
  unlikeMutate: MutationFn
  scrapMutate: MutationFn
  unscrapMutate: MutationFn
  reportMutate: MutationFn<ReportPayload>
}

export function usePostDetailActions({
  post,
  canReport,
  authStore,
  route,
  router,
  toastStore,
  confirm,
  t,
  buildBoardListRoute,
  closeOverflowMenu,
  deleteMutate,
  likeMutate,
  unlikeMutate,
  scrapMutate,
  unscrapMutate,
  reportMutate,
}: UsePostDetailActionsOptions) {
  const isLikeAnimating = ref(false)
  const isBookmarkAnimating = ref(false)
  const showReportModal = ref(false)
  const reportReason = ref('')
  let likeAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let bookmarkAnimationTimer: ReturnType<typeof setTimeout> | null = null

  async function handleDelete() {
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return

    deleteMutate(route.params.postId as string | number, {
      onSuccess: () => {
        if (post.value?.board.boardUrl) {
          router.push(buildBoardListRoute(post.value.board.boardUrl))
        }
      },
      onError: (err) => {
        logger.error('Failed to delete post:', err)
      }
    })
  }

  function triggerLikeAnimation() {
    isLikeAnimating.value = true
    if (likeAnimationTimer) clearTimeout(likeAnimationTimer)
    likeAnimationTimer = setTimeout(() => {
      isLikeAnimating.value = false
      likeAnimationTimer = null
    }, 300)
  }

  function triggerBookmarkAnimation() {
    isBookmarkAnimating.value = true
    if (bookmarkAnimationTimer) clearTimeout(bookmarkAnimationTimer)
    bookmarkAnimationTimer = setTimeout(() => {
      isBookmarkAnimating.value = false
      bookmarkAnimationTimer = null
    }, 300)
  }

  async function handleLike() {
    if (!authStore.isAuthenticated || !post.value) return

    if (post.value.liked) {
      unlikeMutate(route.params.postId as string | number, {
        onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
      })
      return
    }

    triggerLikeAnimation()
    likeMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err)
    })
  }

  async function handleBookmark() {
    if (!authStore.isAuthenticated || !post.value) return

    if (post.value.scrapped) {
      unscrapMutate(route.params.postId as string | number, {
        onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
      })
      return
    }

    triggerBookmarkAnimation()
    scrapMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err)
    })
  }

  function openReportModal() {
    if (!canReport.value) return
    showReportModal.value = true
    reportReason.value = ''
    closeOverflowMenu()
  }

  async function submitReport() {
    if (!reportReason.value.trim()) {
      toastStore.addToast(t('board.postDetail.reportReasonRequired'), 'error')
      return
    }

    reportMutate({
      targetPostId: route.params.postId as string | number,
      reason: reportReason.value
    }, {
      onSuccess: () => {
        toastStore.addToast(t('board.postDetail.reportSuccess'), 'success')
        showReportModal.value = false
      },
      onError: (err) => {
        logger.error('Report failed:', err)
        toastStore.addToast(t('board.postDetail.reportFailed'), 'error')
      }
    })
  }

  onUnmounted(() => {
    if (likeAnimationTimer) {
      clearTimeout(likeAnimationTimer)
      likeAnimationTimer = null
    }
    if (bookmarkAnimationTimer) {
      clearTimeout(bookmarkAnimationTimer)
      bookmarkAnimationTimer = null
    }
  })

  return {
    isLikeAnimating,
    isBookmarkAnimating,
    showReportModal,
    reportReason,
    handleDelete,
    handleLike,
    handleBookmark,
    openReportModal,
    submitReport,
  }
}
