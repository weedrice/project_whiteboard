import { computed, onUnmounted, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router, RouteLocationRaw } from 'vue-router'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent, type AuthSessionIntent, type AuthSessionIntentSource } from '@/utils/authSessionIntent'
import { useConfirm } from '@/composables/useConfirm'
import { usePost } from '@/features/board/posts/queries/usePost'
import { useToastStore } from '@/stores/toast'
import type { Post } from '@/types'
import type { ReportReasonType } from '@/types'
import logger from '@/utils/logger'

interface UsePostDetailActionsOptions {
  post: Ref<Post | null | undefined>
  canReport: ComputedRef<boolean>
  authStore: AuthSessionIntentSource & { isAuthenticated: boolean }
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
  const { mutateAsync: deleteMutate } = useDeletePost()
  const { mutateAsync: likeMutate } = useLikePost()
  const { mutateAsync: unlikeMutate } = useUnlikePost()
  const { mutateAsync: scrapMutate } = useScrapPost()
  const { mutateAsync: unscrapMutate } = useUnscrapPost()
  const { mutateAsync: reportMutate } = useReportPost()
  const isLikeAnimating = ref(false)
  const isBookmarkAnimating = ref(false)
  const operations = useAuthScopedOperation(authStore)
  const isLikeMutationPending = computed(() => operations.isPending('like'))
  const isBookmarkMutationPending = computed(() => operations.isPending('bookmark'))
  const showReportModal = ref(false)
  let likeAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let bookmarkAnimationTimer: ReturnType<typeof setTimeout> | null = null
  let reportRevision = 0
  let reportIntent: {
    postId: string | number
    session: AuthSessionIntent
    revision: number
  } | null = null

  const currentPostId = () => route.params.postId as string | number
  const isIntentCurrent = (postId: string | number, session: AuthSessionIntent) => (
    String(currentPostId()) === String(postId)
    && isAuthSessionIntentCurrent(authStore, session)
  )

  async function handleDelete() {
    const postId = currentPostId()
    const boardUrl = post.value?.board.boardUrl
    await operations.run('delete', async (scope) => {
      const isConfirmed = await confirm(t('common.messages.confirmDelete'))
      scope.assertCurrent()
      if (!isConfirmed || !isIntentCurrent(postId, scope.intent)) return
      try {
        await deleteMutate(postId)
        scope.assertCurrent()
        if (boardUrl && isIntentCurrent(postId, scope.intent)) {
          await router.push(buildBoardListRoute(boardUrl))
        }
      } catch (err) {
        scope.runIfCurrent(() => logger.error('Failed to delete post:', err))
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
    const postId = currentPostId()
    const liked = post.value.liked
    await operations.run('like', async (scope) => {
      if (!liked) triggerLikeAnimation()
      try {
        await (liked ? unlikeMutate(postId) : likeMutate(postId))
      } catch (err) {
        scope.runIfCurrent(() => logger.error(t('board.postDetail.likeFailed'), err))
      }
    })
  }

  async function handleBookmark() {
    if (!authStore.isAuthenticated || !post.value) return
    const postId = currentPostId()
    const scrapped = post.value.scrapped
    await operations.run('bookmark', async (scope) => {
      if (!scrapped) triggerBookmarkAnimation()
      try {
        await (scrapped ? unscrapMutate(postId) : scrapMutate(postId))
      } catch (err) {
        scope.runIfCurrent(() => logger.error(t('board.postDetail.scrapFailed'), err))
      }
    })
  }

  function openReportModal() {
    if (!canReport.value) return
    reportIntent = {
      postId: currentPostId(),
      session: captureAuthSessionIntent(authStore),
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
    if (!intent || !isIntentCurrent(intent.postId, intent.session)) {
      closeReportModal()
      return false
    }
    const isReportIntentCurrent = () => (
      reportIntent === intent
      && intent.revision === reportRevision
      && isIntentCurrent(intent.postId, intent.session)
    )

    const submitted = await operations.run(`report:${intent.revision}`, async (scope) => {
      try {
        await reportMutate({
          targetPostId: intent.postId,
          reason,
          ...(reasonType ? { reasonType } : {}),
        })
        scope.assertCurrent()
        if (!isReportIntentCurrent()) return false
        toastStore.addToast(t('board.postDetail.reportSuccess'), 'success')
        closeReportModal()
        return true
      } catch (err) {
        if (!scope.isCurrent() || !isReportIntentCurrent()) return false
        logger.error('Report failed:', err)
        toastStore.addToast(t('board.postDetail.reportFailed'), 'error')
        return false
      }
    })
    return submitted === true
  }

  useAuthSessionReset(() => {
    closeReportModal()
    if (likeAnimationTimer) clearTimeout(likeAnimationTimer)
    if (bookmarkAnimationTimer) clearTimeout(bookmarkAnimationTimer)
    likeAnimationTimer = null
    bookmarkAnimationTimer = null
    isLikeAnimating.value = false
    isBookmarkAnimating.value = false
  }, authStore)

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
