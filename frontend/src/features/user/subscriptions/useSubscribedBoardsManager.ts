import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { userApi } from '@/api/user'
import { boardApi } from '@/api/board'
import { unwrapApiData } from '@/api/response'
import { invalidateBoardListCaches } from '@/features/board/queries/boardCacheInvalidation'
import { useMobileViewport } from '@/composables/useMediaQuery'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { SubscriptionBoardListItem } from '@/types'

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
  const { confirm } = useConfirm()
  const { handleSilentError, handleError } = useErrorHandler()

  const accessibleBoards = ref<SubscriptionBoardListItem[]>([])
  const unavailableBoards = ref<SubscriptionBoardListItem[]>([])
  const loading = ref(false)
  const isReordering = ref(false)
  const isMobile = useMobileViewport()
  let subscriptionsRequestId = 0

  const hasSubscriptions = computed(() =>
    accessibleBoards.value.length > 0 || unavailableBoards.value.length > 0
  )

  function invalidateSubscriptionCaches() {
    invalidateBoardListCaches(queryClient)
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

  async function fetchAllSubscriptions() {
    const fetchPage = async (page: number) => {
      const { data } = await userApi.getMySubscriptions({
        page,
        size: subscriptionsPageSize,
        includeUnavailable: true
      })

      if (!data.success) {
        return null
      }

      return unwrapApiData(data)
    }

    const firstPage = await fetchPage(0)
    if (firstPage === null) {
      return null
    }

    const remainingPages = Array.from(
      { length: Math.max(firstPage.totalPages - 1, 0) },
      (_, index) => index + 1
    )
    const remainingResults = await Promise.all(remainingPages.map(fetchPage))
    if (remainingResults.some((page) => page === null)) {
      return null
    }

    return [
      ...firstPage.content,
      ...remainingResults.flatMap(page => page?.content ?? [])
    ]
  }

  async function fetchSubscriptions() {
    const requestId = ++subscriptionsRequestId
    loading.value = true
    try {
      const boards = await fetchAllSubscriptions()
      if (requestId !== subscriptionsRequestId) {
        return
      }

      if (boards !== null) {
        accessibleBoards.value = boards.filter(canReorderSubscription)
        unavailableBoards.value = boards.filter(board => !canReorderSubscription(board))
      }
    } catch (error) {
      if (requestId !== subscriptionsRequestId) {
        return
      }

      handleSilentError(error, 'Failed to load subscriptions')
    } finally {
      if (requestId === subscriptionsRequestId) {
        loading.value = false
      }
    }
  }

  async function handleUnsubscribe(board: SubscriptionBoardListItem) {
    const isConfirmed = await confirm(t('user.subscriptions.unsubscribeConfirm'))
    if (!isConfirmed) return
    try {
      const { data } = await boardApi.unsubscribeBoard(board.boardUrl)
      if (data.success) {
        toastStore.addToast(t('user.subscriptions.unsubscribeSuccess'), 'success')
        invalidateSubscriptionCaches()
        await fetchSubscriptions()
      }
    } catch (error) {
      handleError(error, t('user.subscriptions.unsubscribeFailed'))
    }
  }

  async function persistSubscriptionOrder(
    accessibleSnapshot: SubscriptionBoardListItem[],
    unavailableSnapshot: SubscriptionBoardListItem[],
  ) {
    if (isReordering.value) return false

    const boardUrls = accessibleBoards.value.map(board => board.boardUrl)
    isReordering.value = true
    try {
      await boardApi.updateSubscriptionOrder(boardUrls)
      invalidateSubscriptionCaches()
      return true
    } catch (error) {
      handleSilentError(error, 'Failed to update subscription order')
      restoreSubscriptionSnapshot(accessibleSnapshot, unavailableSnapshot)
      await fetchSubscriptions()
      return false
    } finally {
      isReordering.value = false
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

  onUnmounted(() => {
    subscriptionsRequestId += 1
  })

  return {
    accessibleBoards,
    unavailableBoards,
    loading,
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
