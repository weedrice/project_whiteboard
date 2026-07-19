import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { userApi } from '@/api/user'
import { boardApi } from '@/api/board'
import { unwrapApiPageData } from '@/api/response'
import { invalidateBoardListCaches } from '@/features/board/queries/boardCacheInvalidation'
import { useMobileViewport } from '@/composables/useMediaQuery'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { SubscriptionBoardListItem } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'

const subscriptionsPageSize = 100

export function isAccessibleSubscription(board: SubscriptionBoardListItem) {
  return board.accessState === 'ACCESSIBLE'
}

function canReorderSubscription(board: SubscriptionBoardListItem) {
  return isAccessibleSubscription(board) && board.isActive !== false
}

export function useSubscribedBoardsManager() {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const queryClient = useQueryClient()
  const authStore = useAuthStore()
  const { confirm } = useConfirm()
  const { handleSilentError, handleError } = useErrorHandler()

  const accessibleBoards = ref<SubscriptionBoardListItem[]>([])
  const unavailableBoards = ref<SubscriptionBoardListItem[]>([])
  const loading = ref(false)
  const isReordering = ref(false)
  const loadError = ref(false)
  const isMobile = useMobileViewport()
  let subscriptionsRequestId = 0
  let subscriptionsAbortController: AbortController | null = null
  const mutationAbortControllers = new Set<AbortController>()
  let disposed = false

  const hasSubscriptions = computed(() =>
    accessibleBoards.value.length > 0 || unavailableBoards.value.length > 0
  )

  function invalidateSubscriptionCaches() {
    invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
  }

  function cloneBoards(boards: SubscriptionBoardListItem[]) {
    return boards.map((board) => ({ ...board }))
  }

  function restoreSubscriptionSnapshot(
    accessibleSnapshot: SubscriptionBoardListItem[],
    unavailableSnapshot: SubscriptionBoardListItem[],
  ) {
    accessibleBoards.value = cloneBoards(accessibleSnapshot)
    unavailableBoards.value = cloneBoards(unavailableSnapshot)
  }

  async function fetchAllSubscriptions(signal: AbortSignal) {
    const fetchPage = async (page: number) => {
      const { data } = await userApi.getMySubscriptions({
        page,
        size: subscriptionsPageSize,
        includeUnavailable: true
      }, { signal })

      if (!data.success) {
        throw new Error('Subscription response was unsuccessful')
      }

      return unwrapApiPageData(data)
    }

    const firstPage = await fetchPage(0)
    const remainingPages = Array.from(
      { length: Math.max(firstPage.totalPages - 1, 0) },
      (_, index) => index + 1
    )
    const remainingResults = await Promise.all(remainingPages.map(fetchPage))
    return [
      ...firstPage.content,
      ...remainingResults.flatMap(page => page?.content ?? [])
    ]
  }

  async function fetchSubscriptions() {
    const requestId = ++subscriptionsRequestId
    const generation = authStore.sessionGeneration
    subscriptionsAbortController?.abort()
    const controller = new AbortController()
    subscriptionsAbortController = controller
    loading.value = true
    loadError.value = false
    try {
      const boards = await fetchAllSubscriptions(controller.signal)
      if (requestId !== subscriptionsRequestId || generation !== authStore.sessionGeneration) {
        return
      }

      accessibleBoards.value = boards.filter(canReorderSubscription)
      unavailableBoards.value = boards.filter(board => !canReorderSubscription(board))
    } catch (error) {
      if (requestId !== subscriptionsRequestId
        || generation !== authStore.sessionGeneration
        || isCancellationError(error)) {
        return
      }

      loadError.value = true
      handleSilentError(error, 'Failed to load subscriptions')
    } finally {
      if (requestId === subscriptionsRequestId) {
        loading.value = false
      }
      if (subscriptionsAbortController === controller) subscriptionsAbortController = null
    }
  }

  async function handleUnsubscribe(board: SubscriptionBoardListItem) {
    if (isReordering.value) return
    const generation = authStore.sessionGeneration
    const isConfirmed = await confirm(t('user.subscriptions.unsubscribeConfirm'))
    if (!isConfirmed || generation !== authStore.sessionGeneration || isReordering.value) return
    const controller = new AbortController()
    mutationAbortControllers.add(controller)
    try {
      const { data } = await boardApi.unsubscribeBoard(board.boardUrl, { signal: controller.signal })
      if (data.success && generation === authStore.sessionGeneration && !controller.signal.aborted) {
        toastStore.addToast(t('user.subscriptions.unsubscribeSuccess'), 'success')
        invalidateSubscriptionCaches()
        await fetchSubscriptions()
      }
    } catch (error) {
      if (controller.signal.aborted || generation !== authStore.sessionGeneration) return
      handleError(error, t('user.subscriptions.unsubscribeFailed'))
    } finally {
      mutationAbortControllers.delete(controller)
    }
  }

  async function persistSubscriptionOrder(
    accessibleSnapshot: SubscriptionBoardListItem[],
    unavailableSnapshot: SubscriptionBoardListItem[],
  ) {
    if (isReordering.value) return false

    const boardUrls = accessibleBoards.value.map(board => board.boardUrl)
    const generation = authStore.sessionGeneration
    const controller = new AbortController()
    mutationAbortControllers.add(controller)
    isReordering.value = true
    try {
      await boardApi.updateSubscriptionOrder(boardUrls, { signal: controller.signal })
      if (controller.signal.aborted || generation !== authStore.sessionGeneration) return false
      invalidateSubscriptionCaches()
      return true
    } catch (error) {
      if (controller.signal.aborted || generation !== authStore.sessionGeneration) return false
      handleSilentError(error, 'Failed to update subscription order')
      restoreSubscriptionSnapshot(accessibleSnapshot, unavailableSnapshot)
      await fetchSubscriptions()
      return false
    } finally {
      mutationAbortControllers.delete(controller)
      if (generation === authStore.sessionGeneration) isReordering.value = false
    }
  }

  async function handleDragEnd() {
    const accessibleSnapshot = cloneBoards(accessibleBoards.value)
      .sort((left, right) => left.sortOrder - right.sortOrder)
    const unavailableSnapshot = cloneBoards(unavailableBoards.value)
    await persistSubscriptionOrder(accessibleSnapshot, unavailableSnapshot)
  }

  async function moveSubscription(boardUrl: string, direction: 'up' | 'down') {
    if (isReordering.value) return

    const currentIndex = accessibleBoards.value.findIndex(board => board.boardUrl === boardUrl)
    const targetIndex = currentIndex + (direction === 'up' ? -1 : 1)
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= accessibleBoards.value.length) return

    const accessibleSnapshot = cloneBoards(accessibleBoards.value)
    const unavailableSnapshot = cloneBoards(unavailableBoards.value)
    const reorderedBoards = [...accessibleBoards.value]
    const [movedBoard] = reorderedBoards.splice(currentIndex, 1)
    if (!movedBoard) return
    reorderedBoards.splice(targetIndex, 0, movedBoard)
    accessibleBoards.value = reorderedBoards

    await persistSubscriptionOrder(accessibleSnapshot, unavailableSnapshot)
  }

  onMounted(() => {
    fetchSubscriptions()
  })

  const stopSessionBoundary = subscribeAuthSessionBoundary((generation) => {
    subscriptionsRequestId += 1
    subscriptionsAbortController?.abort()
    subscriptionsAbortController = null
    mutationAbortControllers.forEach((controller) => controller.abort())
    mutationAbortControllers.clear()
    accessibleBoards.value = []
    unavailableBoards.value = []
    loading.value = false
    isReordering.value = false
    loadError.value = false
    queueMicrotask(() => {
      if (disposed
        || authStore.sessionGeneration !== generation
        || !authStore.isAuthenticated) return
      void fetchSubscriptions()
    })
  })

  onUnmounted(() => {
    disposed = true
    stopSessionBoundary()
    subscriptionsRequestId += 1
    subscriptionsAbortController?.abort()
    mutationAbortControllers.forEach((controller) => controller.abort())
  })

  return {
    accessibleBoards,
    unavailableBoards,
    loading,
    loadError,
    isReordering,
    isMobile,
    hasSubscriptions,
    fetchSubscriptions,
    handleDragEnd,
    moveSubscription,
    handleUnsubscribe,
    isAccessibleSubscription,
  }
}
