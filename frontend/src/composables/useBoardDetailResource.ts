import { computed, ref, watch, type Ref } from 'vue'
import { useBoard } from '@/composables/useBoard'
import { useMobileViewport } from '@/composables/useMediaQuery'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import { resolveDefaultCategory } from '@/utils/board'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import type { PostSummary } from '@/types'

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
  const { useBoardDetail, useBoardPosts, useInfiniteBoardPosts, useBoardNotices, useSubscribeBoard } = useBoard()
  const isMobilePostList = useMobileViewport()
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
    return decodeBoardUrlTitle(boardUrl.value || '') || t('board.seo.spaceTitleFallback')
  })

  const boardContentEnabled = computed(() => !boardError.value)
  const pagedPostsEnabled = computed(() => boardContentEnabled.value && !isMobilePostList.value)
  const infinitePostsEnabled = computed(() => boardContentEnabled.value && isMobilePostList.value)
  const {
    data: postsData,
    isLoading: isPostsLoading,
    isFetching: isPostsFetching,
    error: postsError
  } = useBoardPosts(boardUrl, queryParams, isSearching, pagedPostsEnabled, {
    meta: { errorMessage: false },
    requestConfig: { skipGlobalErrorHandler: true }
  })
  const {
    data: infinitePostsData,
    isLoading: isInfinitePostsLoading,
    isFetching: isInfinitePostsFetching,
    isFetchingNextPage: isFetchingNextPostPage,
    hasNextPage: hasMorePosts,
    fetchNextPage,
    error: infinitePostsError
  } = useInfiniteBoardPosts(boardUrl, queryParams, isSearching, infinitePostsEnabled, {
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
    items: pagedPosts,
    totalPages: pagedTotalPages,
  } = usePageResponseState(postsData, fallbackPostPage)
  const infinitePosts = computed<PostSummary[]>(() => (
    infinitePostsData.value?.pages.flatMap((page) => page.content ?? []) ?? []
  ))
  const posts = computed(() => (
    isMobilePostList.value ? infinitePosts.value : pagedPosts.value
  ))
  const totalPages = computed(() => (
    isMobilePostList.value
      ? (infinitePostsData.value?.pages[0]?.totalPages ?? 0)
      : pagedTotalPages.value
  ))
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
    isMobilePostList.value
      ? (
        (isInfinitePostsLoading.value && posts.value.length === 0)
        || (isInfinitePostsFetching.value && !isFetchingNextPostPage.value && currentListKey.value !== lastResolvedListKey.value)
      )
      : (
        (isPostsLoading.value && posts.value.length === 0)
        || (isPostsFetching.value && currentListKey.value !== lastResolvedListKey.value)
      )
  ))
  const activePostsError = computed(() => (
    isMobilePostList.value ? infinitePostsError.value : postsError.value
  ))

  const blockingError = computed(() => {
    const sourceError = boardError.value ?? (posts.value.length === 0 ? activePostsError.value : null)
    if (!sourceError) return ''
    if (isRestrictedResourceError(sourceError)) {
      return t('board.detail.restricted')
    }
    return t('board.loadFailed')
  })

  const transientListError = computed(() => {
    if (!activePostsError.value || posts.value.length === 0) {
      return ''
    }
    return t('board.loadFailed')
  })

  watch([currentListKey, isPostsFetching, isInfinitePostsFetching], ([nextListKey, fetching, infiniteFetching]) => {
    if (!fetching && !infiniteFetching) {
      lastResolvedListKey.value = nextListKey
    }
  }, { immediate: true })

  function loadMorePosts() {
    if (!isMobilePostList.value || isFetchingNextPostPage.value || !hasMorePosts.value) {
      return
    }
    fetchNextPage()
  }

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
    isFetchingNextPostPage,
    isMobilePostList,
    isNoticesExpanded,
    isSubscribePending,
    posts,
    hasMorePosts,
    loadMorePosts,
    showPostListLoading,
    subscribeMutate,
    totalPages,
    transientListError,
    visibleNotices,
    notices,
    resetNoticeState,
  }
}
