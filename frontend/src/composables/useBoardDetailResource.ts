import { computed, ref, watch, type Ref } from 'vue'
import { useBoard } from '@/composables/useBoard'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import { resolveDefaultCategory } from '@/utils/board'
import { isRestrictedResourceError } from '@/utils/errorHandler'

const NOTICE_PREVIEW_LIMIT = 3

interface BoardDetailPostParams {
  page?: number
  size?: number
  categoryId?: number
  minLikes?: number
  sort?: string
  q?: string
  searchType?: string
}

interface UseBoardDetailResourceOptions {
  boardUrl: Ref<string>
  queryParams: Ref<BoardDetailPostParams>
  isSearching: Ref<boolean>
  t: (key: string) => string
}

function decodeBoardUrlTitle(rawBoardUrl: string): string {
  try {
    return decodeURIComponent(rawBoardUrl).trim()
  } catch {
    return rawBoardUrl.trim()
  }
}

export function useBoardDetailResource({
  boardUrl,
  queryParams,
  isSearching,
  t
}: UseBoardDetailResourceOptions) {
  const { useBoardDetail, useBoardPosts, useBoardNotices, useSubscribeBoard } = useBoard()
  const {
    data: board,
    isLoading: isBoardLoading,
    error: boardError
  } = useBoardDetail(boardUrl, {
    meta: { errorMessage: false },
    requestConfig: { skipGlobalErrorHandler: true }
  })

  const boardTitle = computed(() => {
    const boardName = board.value?.boardName?.trim()
    if (boardName) return boardName
    return decodeBoardUrlTitle(boardUrl.value || '') || 'Space'
  })

  const boardContentEnabled = computed(() => !boardError.value)
  const {
    data: postsData,
    isLoading: isPostsLoading,
    isFetching: isPostsFetching,
    error: postsError
  } = useBoardPosts(boardUrl, queryParams, isSearching, boardContentEnabled, {
    meta: { errorMessage: false },
    requestConfig: { skipGlobalErrorHandler: true }
  })

  const {
    data: noticesData
  } = useBoardNotices(boardUrl, boardContentEnabled, {
    meta: { errorMessage: false },
    requestConfig: { skipGlobalErrorHandler: true }
  })

  const {
    mutate: subscribeMutate,
    isPending: isSubscribePending
  } = useSubscribeBoard({
    meta: { errorMessage: false }
  })

  const defaultCategory = computed(() => resolveDefaultCategory(board.value?.categories))
  const categories = computed(() => (
    board.value?.categories?.filter((category) => category.categoryId !== defaultCategory.value?.categoryId) ?? []
  ))
  const fallbackPostPage = computed(() => queryParams.value.page ?? 0)
  const {
    items: posts,
    totalPages,
  } = usePageResponseState(postsData, fallbackPostPage)
  const isNoticesExpanded = ref(false)
  const notices = computed(() => (
    [...(noticesData.value ?? [])].sort((left, right) => {
      const leftTime = new Date(left.createdAt).getTime()
      const rightTime = new Date(right.createdAt).getTime()
      if (leftTime !== rightTime) {
        return rightTime - leftTime
      }
      return Number(right.postId ?? 0) - Number(left.postId ?? 0)
    })
  ))
  const visibleNotices = computed(() => (
    isNoticesExpanded.value ? notices.value : notices.value.slice(0, NOTICE_PREVIEW_LIMIT)
  ))
  const hasNoticeOverflow = computed(() => notices.value.length > NOTICE_PREVIEW_LIMIT)
  const isInitialLoading = computed(() => isBoardLoading.value && !board.value)
  const currentListKey = computed(() => JSON.stringify({
    boardUrl: boardUrl.value,
    ...queryParams.value
  }))
  const lastResolvedListKey = ref(currentListKey.value)
  const showPostListLoading = computed(() => (
    (isPostsLoading.value && posts.value.length === 0)
    || (isPostsFetching.value && currentListKey.value !== lastResolvedListKey.value)
  ))

  const blockingError = computed(() => {
    const sourceError = boardError.value ?? (posts.value.length === 0 ? postsError.value : null)
    if (!sourceError) return ''
    if (isRestrictedResourceError(sourceError)) {
      return 'This board is restricted.'
    }
    return t('board.loadFailed')
  })

  const transientListError = computed(() => {
    if (!postsError.value || posts.value.length === 0) {
      return ''
    }
    return t('board.loadFailed')
  })

  watch([currentListKey, isPostsFetching], ([nextListKey, fetching]) => {
    if (!fetching) {
      lastResolvedListKey.value = nextListKey
    }
  }, { immediate: true })

  function resetNoticeState() {
    isNoticesExpanded.value = false
  }

  return {
    board,
    boardTitle,
    blockingError,
    categories,
    hasNoticeOverflow,
    isInitialLoading,
    isNoticesExpanded,
    isSubscribePending,
    posts,
    showPostListLoading,
    subscribeMutate,
    totalPages,
    transientListError,
    visibleNotices,
    notices,
    resetNoticeState,
  }
}
