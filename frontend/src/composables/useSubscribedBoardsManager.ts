import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { userApi } from '@/api/user'
import { boardApi } from '@/api/board'
import { boardQueryKeys } from '@/composables/boardQueryKeys'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { SubscriptionBoardListItem } from '@/types'

const subscriptionsPageSize = 100
const mobileMediaQuery = '(max-width: 639px)'
const mobileViewportMaxWidth = 639

export function isAccessibleSubscription(board: SubscriptionBoardListItem) {
  return board.accessState === 'ACCESSIBLE'
}

function canReorderSubscription(board: SubscriptionBoardListItem) {
  return isAccessibleSubscription(board) && board.isActive !== false
}

function createMobileMediaQuery() {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(mobileMediaQuery)
    : null
}

function isMobileViewportFallback() {
  return typeof window !== 'undefined' && window.innerWidth <= mobileViewportMaxWidth
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
  const mediaQuery = createMobileMediaQuery()
  const isMobile = ref(mediaQuery?.matches ?? isMobileViewportFallback())
  let subscriptionsRequestId = 0

  const hasSubscriptions = computed(() =>
    accessibleBoards.value.length > 0 || unavailableBoards.value.length > 0
  )

  function updateIsMobile(event?: MediaQueryListEvent) {
    isMobile.value = event?.matches ?? mediaQuery?.matches ?? isMobileViewportFallback()
  }

  function invalidateSubscriptionCaches() {
    queryClient.invalidateQueries({ queryKey: boardQueryKeys.all })
    queryClient.invalidateQueries({ queryKey: boardQueryKeys.subscriptions })
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

      return data.data
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

  async function handleDragEnd() {
    const boardUrls = accessibleBoards.value.map(board => board.boardUrl)
    try {
      await boardApi.updateSubscriptionOrder(boardUrls)
      invalidateSubscriptionCaches()
    } catch (error) {
      handleSilentError(error, 'Failed to update subscription order')
      await fetchSubscriptions()
    }
  }

  onMounted(() => {
    fetchSubscriptions()
    updateIsMobile()
    mediaQuery?.addEventListener('change', updateIsMobile)
  })

  onUnmounted(() => {
    subscriptionsRequestId += 1
    mediaQuery?.removeEventListener('change', updateIsMobile)
  })

  return {
    accessibleBoards,
    unavailableBoards,
    loading,
    isMobile,
    hasSubscriptions,
    fetchSubscriptions,
    handleDragEnd,
    handleUnsubscribe,
    isAccessibleSubscription,
  }
}
