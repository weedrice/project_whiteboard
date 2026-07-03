import { onUnmounted, ref, type ComputedRef, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router, RouteLocationRaw } from 'vue-router'
import { useConfirm } from '@/composables/useConfirm'
import { usePost } from '@/features/board/posts/queries/usePost'
import { useToastStore } from '@/stores/toast'
import type { Post } from '@/types'
import logger from '@/utils/logger'

interface UsePostDetailActionsOptions {
  post: Ref<Post | null | undefined>
  canReport: ComputedRef<boolean>
  authStore: { isAuthenticated: boolean }
  route: RouteLocationNormalizedLoaded
  router: Router
  t: (key: string) => string
  buildBoardListRoute: (boardUrl: string) => RouteLocationRaw
  closeOverflowMenu: () => void
}

export function usePostDetailActions({
  post,
  canReport,
  authStore,
  route,
  router,
  t,
  buildBoardListRoute,
  closeOverflowMenu,
}: UsePostDetailActionsOptions) {
  const toastStore = useToastStore()
  const { confirm } = useConfirm()
  const {
    useDeletePost,
    useLikePost,
    useReportPost,
    useScrapPost,
    useUnlikePost,
    useUnscrapPost,
  } = usePost()
  const { mutate: deleteMutate } = useDeletePost()
  const { mutate: likeMutate } = useLikePost()
  const { mutate: unlikeMutate } = useUnlikePost()
  const { mutate: scrapMutate } = useScrapPost()
  const { mutate: unscrapMutate } = useUnscrapPost()
  const { mutate: reportMutate } = useReportPost()
  const isLikeAnimating = ref(false)
  const isBookmarkAnimating = ref(false)
  const showReportModal = ref(false)
  let likeAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let bookmarkAnimationTimer: ReturnType<typeof setTimeout> | null = null

  async function handleDelete() {
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return

    const boardUrl = post.value?.board.boardUrl
    deleteMutate(route.params.postId as string | number, {
      onSuccess: () => {
        if (boardUrl) {
          router.push(buildBoardListRoute(boardUrl))
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
    closeOverflowMenu()
  }

  async function submitReport(reason: string) {
    return await new Promise<boolean>((resolve) => {
      reportMutate({
        targetPostId: route.params.postId as string | number,
        reason,
      }, {
        onSuccess: () => {
          toastStore.addToast(t('board.postDetail.reportSuccess'), 'success')
          showReportModal.value = false
          resolve(true)
        },
        onError: (err) => {
          logger.error('Report failed:', err)
          toastStore.addToast(t('board.postDetail.reportFailed'), 'error')
          resolve(false)
        },
      })
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
    handleDelete,
    handleLike,
    handleBookmark,
    openReportModal,
    submitReport,
  }
}
