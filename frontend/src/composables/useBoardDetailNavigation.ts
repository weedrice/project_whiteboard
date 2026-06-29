import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { LocationQuery, RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { useBoardDetailShortcuts } from '@/composables/useKeyboardShortcuts'
import type { BoardDetail, PostSummary } from '@/types'

export const BOARD_DETAIL_SEARCH_INPUT_ID = 'board-search-input'

interface UseBoardDetailNavigationOptions {
  route: RouteLocationNormalizedLoaded
  router: Router
  boardUrl: ComputedRef<string>
  currentPostId: ComputedRef<string | undefined>
  page: Ref<number>
  totalPages: ComputedRef<number>
  board: Ref<BoardDetail | null | undefined>
  canWrite: ComputedRef<boolean>
  isSubscribePending: Ref<boolean>
  handleSubscribe: () => Promise<void>
  handlePageChange: (newPage: number, totalPages: number) => void
  resetListState: () => void
  resetNoticeState: () => void
}

function hasInteractiveFocus(): boolean {
  if (typeof document === 'undefined') {
    return false
  }

  const activeElement = document.activeElement as HTMLElement | null
  if (!activeElement || activeElement === document.body) {
    return false
  }

  if (activeElement.isContentEditable) {
    return true
  }

  const tagName = activeElement.tagName.toLowerCase()
  return ['input', 'textarea', 'select', 'button', 'a'].includes(tagName)
}

export function useBoardDetailNavigation({
  route,
  router,
  boardUrl,
  currentPostId,
  page,
  totalPages,
  board,
  canWrite,
  isSubscribePending,
  handleSubscribe,
  handlePageChange,
  resetListState,
  resetNoticeState
}: UseBoardDetailNavigationOptions) {
  const suppressedCurrentPostId = ref<string | null>(null)
  const listQuery = computed<LocationQuery>(() => {
    const { fromCreate: _fromCreate, ...query } = route.query
    return query
  })
  const highlightedPostId = computed(() => (
    currentPostId.value && currentPostId.value !== suppressedCurrentPostId.value
      ? currentPostId.value
      : undefined
  ))

  const buildBoardListRoute = () => ({
    path: `/board/${boardUrl.value}`,
    query: listQuery.value
  })

  function onPageChange(newPage: number) {
    handlePageChange(newPage, totalPages.value)
  }

  function getNoticeRoute(notice: PostSummary) {
    return {
      path: `/board/${boardUrl.value}/post/${notice.postId}`,
      query: listQuery.value
    }
  }

  watch(() => route.params.boardUrl, () => {
    resetListState()
    resetNoticeState()
    suppressedCurrentPostId.value = null
  })

  watch([currentPostId, () => route.query.fromCreate], ([postId, fromCreate]) => {
    if (!postId) {
      suppressedCurrentPostId.value = null
      return
    }

    if (fromCreate === '1') {
      suppressedCurrentPostId.value = postId
      router.replace({
        path: route.path,
        query: listQuery.value
      })
    }
  }, { immediate: true })

  useBoardDetailShortcuts({
    goToNextPage: () => {
      if (page.value < totalPages.value - 1) {
        page.value++
      }
    },
    goToPrevPage: () => {
      if (page.value > 0) {
        page.value--
      }
    },
    goToFirstPage: () => {
      page.value = 0
    },
    goToLastPage: () => {
      if (totalPages.value > 0) {
        page.value = totalPages.value - 1
      }
    },
    goToWrite: () => {
      if (board.value) {
        router.push(`/board/${board.value.boardUrl}/write`)
      }
    },
    toggleSubscribe: handleSubscribe,
    canWrite: () => canWrite.value && !!board.value,
    canGoNext: () => page.value < totalPages.value - 1,
    canGoPrev: () => page.value > 0,
    canToggleSubscribe: () => !isSubscribePending.value,
    shouldIgnoreShortcut: hasInteractiveFocus
  })

  return {
    buildBoardListRoute,
    getNoticeRoute,
    highlightedPostId,
    listQuery,
    onPageChange,
    searchInputElementId: BOARD_DETAIL_SEARCH_INPUT_ID,
  }
}
