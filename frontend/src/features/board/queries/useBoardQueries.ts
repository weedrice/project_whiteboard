import { computed, type Ref } from 'vue'
import { useInfiniteQuery, useQuery } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'
import { unwrapAxiosApiData } from '@/api/response'
import { boardApi } from '@/api/board'
import { userApi } from '@/api/user'
import { searchApi } from '@/api/search'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import type { BoardDetail, BoardListItem, BoardManagerCandidate, PostSummary, SubscriptionBoardListItem } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { optionalQuerySignal } from '@/utils/querySignal'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'

export interface BoardPostParams {
  page?: number
  size?: number
  categoryId?: number
  minLikes?: number
  sort?: string
  q?: string
  searchType?: string
}

type InfiniteBoardPostParams = Omit<BoardPostParams, 'page'>

export interface BoardManagerCandidateParams {
  page?: number
  size?: number
  q?: string
}

export const boardDetailQueryKey = (boardUrl: string) => boardQueryKeys.detail(boardUrl)

export async function fetchBoardDetail(boardUrl: string, requestConfig?: AxiosRequestConfig) {
  const response = requestConfig
    ? await boardApi.getBoard(boardUrl, requestConfig)
    : await boardApi.getBoard(boardUrl)
  return unwrapAxiosApiData(response) as BoardDetail
}

export async function fetchBoardPosts(
  boardUrl: string,
  params: BoardPostParams,
  isSearching = false,
  requestConfig?: AxiosRequestConfig
) {
  if (isSearching) {
    const { searchType, ...restParams } = params
    const response = requestConfig
      ? await searchApi.searchPosts({ ...restParams, searchType, boardUrl }, requestConfig)
      : await searchApi.searchPosts({ ...restParams, searchType, boardUrl })

    return unwrapAxiosApiData(response)
  }

  const response = requestConfig
    ? await boardApi.getPosts(boardUrl, params, requestConfig)
    : await boardApi.getPosts(boardUrl, params)

  return unwrapAxiosApiData(response)
}

export const createBoardDetailQueryOptions = (boardUrl: string, requestConfig?: AxiosRequestConfig) => ({
  queryKey: boardDetailQueryKey(boardUrl),
  queryFn: (context?: { signal?: AbortSignal }) => fetchBoardDetail(
    boardUrl,
    optionalQuerySignal(requestConfig, context),
  ),
  staleTime: QUERY_STALE_TIME.SHORT,
})

const isVisibleSubscriptionBoard = (
  board: SubscriptionBoardListItem
): board is SubscriptionBoardListItem & BoardListItem =>
  board.accessState === 'ACCESSIBLE'

const callWithOptionalConfig = <T>(
  config: AxiosRequestConfig | undefined,
  requestWithConfig: (config: AxiosRequestConfig) => T,
  requestWithoutConfig: () => T,
) => (config ? requestWithConfig(config) : requestWithoutConfig())

export function useBoardQueries() {
  const useBoards = () => {
    return useApiQuery<BoardListItem[]>({
      queryKey: boardQueryKeys.all,
      request: (context) => callWithOptionalConfig(
        optionalQuerySignal({ skipGlobalErrorHandler: true }, context),
        (config) => boardApi.getBoards(config),
        () => boardApi.getBoards(),
      ),
      staleTime: QUERY_STALE_TIME.MEDIUM,
      meta: { errorMessage: false },
    })
  }

  const useSubscribedBoards = (size: number = 10, enabled?: Ref<boolean> | boolean) => {
    const enabledValue = enabled !== undefined
      ? (typeof enabled === 'boolean' ? computed(() => enabled) : enabled)
      : computed(() => false)
    return useApiPageQuery<SubscriptionBoardListItem, BoardListItem[]>({
      queryKey: boardQueryKeys.subscriptionsBySize(size),
      request: (context) => callWithOptionalConfig(
        optionalQuerySignal(undefined, context),
        (config) => userApi.getMySubscriptions({ size }, config),
        () => userApi.getMySubscriptions({ size }),
      ),
      selectData: (page) => page.content.filter(isVisibleSubscriptionBoard),
      staleTime: QUERY_STALE_TIME.MEDIUM,
      enabled: enabledValue,
      keepPreviousData: false,
      meta: AUTH_SCOPED_QUERY_META,
    })
  }

  const useBoardDetail = (
    boardUrl: Ref<string>,
    options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
  ) => {
    const { requestConfig, ...queryOptions } = options
    return useQuery({
      queryKey: computed(() => boardDetailQueryKey(boardUrl.value)),
      queryFn: (context?: { signal?: AbortSignal }) => fetchBoardDetail(
        boardUrl.value,
        optionalQuerySignal(requestConfig, context),
      ),
      staleTime: QUERY_STALE_TIME.SHORT,
      enabled: computed(() => !!boardUrl.value),
      ...queryOptions,
    })
  }

  const useBoardPosts = (
    boardUrl: Ref<string>,
    params: Ref<BoardPostParams>,
    isSearching?: Ref<boolean>,
    enabled?: Ref<boolean>,
    options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
  ) => {
    const { requestConfig, ...queryOptions } = options
    return useQuery({
      queryKey: computed(() => boardQueryKeys.posts(boardUrl.value, params.value, isSearching?.value)),
      queryFn: (context?: { signal?: AbortSignal }) => fetchBoardPosts(
        boardUrl.value,
        params.value,
        Boolean(isSearching?.value),
        optionalQuerySignal(requestConfig, context),
      ),
      enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
      placeholderData: (previousData) => previousData,
      ...queryOptions,
    })
  }

  const useInfiniteBoardPosts = (
    boardUrl: Ref<string>,
    params: Ref<BoardPostParams>,
    isSearching?: Ref<boolean>,
    enabled?: Ref<boolean>,
    options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
  ) => {
    const { requestConfig, ...queryOptions } = options
    const infiniteParams = computed<InfiniteBoardPostParams>(() => {
      const { page: _page, ...restParams } = params.value
      return restParams
    })

    return useInfiniteQuery({
      queryKey: computed(() => boardQueryKeys.infinitePosts(
        boardUrl.value,
        infiniteParams.value,
        isSearching?.value,
      )),
      initialPageParam: params.value.page ?? 0,
      queryFn: ({ pageParam, signal }) => fetchBoardPosts(
        boardUrl.value,
        {
          ...infiniteParams.value,
          page: Number(pageParam),
        },
        Boolean(isSearching?.value),
        optionalQuerySignal(requestConfig, { signal }),
      ),
      getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
      enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
      ...queryOptions,
    })
  }

  const useBoardNotices = (
    boardUrl: Ref<string>,
    enabled?: Ref<boolean>,
    options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
  ) => {
    const { requestConfig, ...queryOptions } = options
    return useApiQuery<PostSummary[]>({
      queryKey: computed(() => boardQueryKeys.notices(boardUrl.value)),
      request: (context) => callWithOptionalConfig(
        optionalQuerySignal(requestConfig, context),
        (config) => boardApi.getNotices(boardUrl.value, config),
        () => boardApi.getNotices(boardUrl.value),
      ),
      enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
      staleTime: QUERY_STALE_TIME.SHORT,
      ...queryOptions,
    })
  }

  const useBoardCategories = (boardUrl: Ref<string>, options = {}) => {
    return useApiQuery({
      queryKey: computed(() => boardQueryKeys.categories(boardUrl.value)),
      request: (context) => callWithOptionalConfig(
        optionalQuerySignal(undefined, context),
        (config) => boardApi.getCategories(boardUrl.value, config),
        () => boardApi.getCategories(boardUrl.value),
      ),
      enabled: computed(() => !!boardUrl.value),
      ...options,
    })
  }

  const useBoardManagerCandidates = (
    boardUrl: Ref<string>,
    params: Ref<BoardManagerCandidateParams>,
    enabled?: Ref<boolean>
  ) => {
    return useApiPageQuery<BoardManagerCandidate>({
      queryKey: computed(() => boardQueryKeys.managerCandidates(boardUrl.value, params.value)),
      request: (context) => callWithOptionalConfig(
        optionalQuerySignal(undefined, context),
        (config) => boardApi.getBoardManagerCandidates(boardUrl.value, params.value, config),
        () => boardApi.getBoardManagerCandidates(boardUrl.value, params.value),
      ),
      enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
    })
  }

  return {
    useBoards,
    useSubscribedBoards,
    useBoardDetail,
    useBoardPosts,
    useInfiniteBoardPosts,
    useBoardNotices,
    useBoardCategories,
    useBoardManagerCandidates,
  }
}
