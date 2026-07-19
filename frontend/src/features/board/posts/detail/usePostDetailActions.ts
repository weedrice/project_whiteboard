import { onUnmounted, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router, RouteLocationRaw } from 'vue-router'
import { useConfirm } from '@/composables/useConfirm'
import { usePost } from '@/features/board/posts/queries/usePost'
import { useToastStore } from '@/stores/toast'
import type { Post } from '@/types'
import type { ReportReasonType } from '@/types'
import logger from '@/utils/logger'

interface UsePostDetailActionsOptions {
  post: Ref<Post | null | undefined>
  canReport: ComputedRef<boolean>
  authStore: { isAuthenticated: boolean, sessionGeneration: number }
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
  const isLikeMutationPending = ref(false)
  const isBookmarkMutationPending = ref(false)
  const showReportModal = ref(false)
  let likeAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let bookmarkAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let reportRevision = 0
  let reportIntent: {
    postId: string | number
    sessionGeneration: number
    revision: number
  } | null = null

  const currentPostId = () => route.params.postId as string | number
  const isIntentCurrent = (postId: string | number, sessionGeneration: number) => (
    String(currentPostId()) === String(postId)
    && authStore.sessionGeneration === sessionGeneration
  )

  async function handleDelete() {
    const postId = currentPostId()
    const boardUrl = post.value?.board.boardUrl
    const sessionGeneration = authStore.sessionGeneration
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed || !isIntentCurrent(postId, sessionGeneration)) return

    deleteMutate(postId, {
      onSuccess: () => {
        if (boardUrl && isIntentCurrent(postId, sessionGeneration)) {
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
    if (!authStore.isAuthenticated || !post.value || isLikeMutationPending.value) return

    isLikeMutationPending.value = true
    const onSettled = () => {
      isLikeMutationPending.value = false
    }

    if (post.value.liked) {
      unlikeMutate(route.params.postId as string | number, {
        onError: (err) => logger.error(t('board.postDetail.likeFailed'), err),
        onSettled,
      })
      return
    }

    triggerLikeAnimation()
    likeMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.likeFailed'), err),
      onSettled,
    })
  }

  async function handleBookmark() {
    if (!authStore.isAuthenticated || !post.value || isBookmarkMutationPending.value) return

    isBookmarkMutationPending.value = true
    const onSettled = () => {
      isBookmarkMutationPending.value = false
    }

    if (post.value.scrapped) {
      unscrapMutate(route.params.postId as string | number, {
        onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err),
        onSettled,
      })
      return
    }

    triggerBookmarkAnimation()
    scrapMutate(route.params.postId as string | number, {
      onError: (err) => logger.error(t('board.postDetail.scrapFailed'), err),
      onSettled,
    })
  }

  function openReportModal() {
    if (!canReport.value) return
    reportIntent = {
      postId: currentPostId(),
      sessionGeneration: authStore.sessionGeneration,
      revision: ++reportRevision,
    }
    showReportModal.value = true
    closeOverflowMenu()
  }

  function closeReportModal() {
    reportRevision += 1
    reportIntent = null
    showReportModal.value = false
  }

  async function submitReport(reason: string, reasonType?: ReportReasonType) {
    const intent = reportIntent
    if (!intent || !isIntentCurrent(intent.postId, intent.sessionGeneration)) {
      closeReportModal()
      return false
    }
    const isReportIntentCurrent = () => (
      reportIntent === intent
      && intent.revision === reportRevision
      && isIntentCurrent(intent.postId, intent.sessionGeneration)
    )

    return await new Promise<boolean>((resolve) => {
      reportMutate({
        targetPostId: intent.postId,
        reason,
        ...(reasonType ? { reasonType } : {}),
      }, {
        onSuccess: () => {
          if (!isReportIntentCurrent()) {
            resolve(false)
            return
          }
          toastStore.addToast(t('board.postDetail.reportSuccess'), 'success')
          closeReportModal()
          resolve(true)
        },
        onError: (err) => {
          if (!isReportIntentCurrent()) {
            resolve(false)
            return
          }
          logger.error('Report failed:', err)
          toastStore.addToast(t('board.postDetail.reportFailed'), 'error')
          resolve(false)
        },
      })
    })
  }

  watch(() => route.params.postId, () => {
    closeReportModal()
  })

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
    isLikeMutationPending,
    isBookmarkMutationPending,
    showReportModal,
    handleDelete,
    handleLike,
    handleBookmark,
    openReportModal,
    closeReportModal,
    submitReport,
  }
}
