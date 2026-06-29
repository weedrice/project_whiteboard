import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { unwrapAxiosApiData } from '@/api/response'
import { boardApi } from '@/api/board'
import type { BoardCreateData, BoardUpdateData } from '@/types'
import { userApi } from '@/api/user'
import { searchApi } from '@/api/search'
import { computed, type Ref } from 'vue'
import { boardQueryKeys } from '@/composables/boardQueryKeys'
import { invalidateBoardListCaches } from '@/composables/boardCacheInvalidation'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import type { BoardDetail, BoardListItem, BoardManagerCandidate, PostSummary, SubscriptionBoardListItem } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'

interface BoardPostParams {
    page?: number
    size?: number
    categoryId?: number
    minLikes?: number
    sort?: string
    q?: string
    searchType?: string
}

interface BoardManagerCandidateParams {
    page?: number
    size?: number
    q?: string
}

export const boardDetailQueryKey = (boardUrl: string | Ref<string>) => boardQueryKeys.detail(boardUrl)

export async function fetchBoardDetail(boardUrl: string, requestConfig?: AxiosRequestConfig) {
    const response = requestConfig
        ? await boardApi.getBoard(boardUrl, requestConfig)
        : await boardApi.getBoard(boardUrl)
    return unwrapAxiosApiData(response) as BoardDetail
}

export const createBoardDetailQueryOptions = (boardUrl: string | Ref<string>, requestConfig?: AxiosRequestConfig) => ({
    queryKey: boardDetailQueryKey(boardUrl),
    queryFn: () => fetchBoardDetail(typeof boardUrl === 'string' ? boardUrl : boardUrl.value, requestConfig),
    staleTime: QUERY_STALE_TIME.SHORT,
})

const isVisibleSubscriptionBoard = (
    board: SubscriptionBoardListItem
): board is SubscriptionBoardListItem & BoardListItem =>
    board.accessState === 'ACCESSIBLE'

export function useBoard() {
    const queryClient = useQueryClient()

    // Fetch all boards
    const useBoards = () => {
        return useApiQuery<BoardListItem[]>({
            queryKey: boardQueryKeys.all,
            request: () => boardApi.getBoards(),
            staleTime: QUERY_STALE_TIME.MEDIUM, // 5 minutes
        })
    }

    // Fetch subscribed boards
    const useSubscribedBoards = (size: number = 10, enabled?: Ref<boolean> | boolean) => {
        const enabledValue = enabled !== undefined
            ? (typeof enabled === 'boolean' ? computed(() => enabled) : enabled)
            : computed(() => false)
        return useApiPageQuery<SubscriptionBoardListItem, BoardListItem[]>({
            queryKey: boardQueryKeys.subscriptionsBySize(size),
            request: () => userApi.getMySubscriptions({ size }),
            selectData: (page) => page.content.filter(isVisibleSubscriptionBoard),
            staleTime: QUERY_STALE_TIME.MEDIUM, // 5 minutes
            enabled: enabledValue,
            keepPreviousData: false,
        })
    }

    // Fetch single board details
    const useBoardDetail = (boardUrl: Ref<string>, options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}) => {
        const { requestConfig, ...queryOptions } = options
        return useQuery({
            ...createBoardDetailQueryOptions(boardUrl, requestConfig),
            enabled: computed(() => !!boardUrl.value),
            ...queryOptions
        })
    }

    // Fetch posts for a board (supports search)
    const useBoardPosts = (
        boardUrl: Ref<string>,
        params: Ref<BoardPostParams>,
        isSearching?: Ref<boolean>,
        enabled?: Ref<boolean>,
        options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
    ) => {
        const { requestConfig, ...queryOptions } = options
        return useQuery({
            queryKey: boardQueryKeys.posts(boardUrl, params, isSearching),
            queryFn: async () => {
                if (isSearching?.value) {
                    const { searchType, ...restParams } = params.value
                    const searchParams = {
                        ...restParams,
                        searchType,
                        boardUrl: boardUrl.value
                    }
                    const response = requestConfig
                        ? await searchApi.searchPosts(searchParams, requestConfig)
                        : await searchApi.searchPosts(searchParams)
                    return unwrapAxiosApiData(response)
                } else {
                    const response = requestConfig
                        ? await boardApi.getPosts(boardUrl.value, params.value, requestConfig)
                        : await boardApi.getPosts(boardUrl.value, params.value)
                    return unwrapAxiosApiData(response)
                }
            },
            enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
            placeholderData: (previousData) => previousData,
            ...queryOptions
        })
    }

    const useBoardNotices = (
        boardUrl: Ref<string>,
        enabled?: Ref<boolean>,
        options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
    ) => {
        const { requestConfig, ...queryOptions } = options
        return useApiQuery<PostSummary[]>({
            queryKey: boardQueryKeys.notices(boardUrl),
            request: () => boardApi.getNotices(boardUrl.value, requestConfig),
            enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
            staleTime: QUERY_STALE_TIME.SHORT,
            ...queryOptions
        })
    }

    // Subscribe/Unsubscribe mutation
    const useSubscribeBoard = (options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}) => {
        const { requestConfig, ...mutationOptions } = options
        return useMutation({
            mutationFn: async ({ boardUrl, isSubscribed }: { boardUrl: string, isSubscribed: boolean }) => {
                if (isSubscribed) {
                    if (requestConfig) {
                        await boardApi.unsubscribeBoard(boardUrl, requestConfig)
                        return
                    }
                    await boardApi.unsubscribeBoard(boardUrl)
                } else {
                    if (requestConfig) {
                        await boardApi.subscribeBoard(boardUrl, requestConfig)
                        return
                    }
                    await boardApi.subscribeBoard(boardUrl)
                }
            },
            onSuccess: (_, { boardUrl }) => {
                // Invalidate board details and boards list to refresh subscription status
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
                invalidateBoardListCaches(queryClient)
            },
            ...mutationOptions
        })
    }

    // Fetch categories for a board
    const useBoardCategories = (boardUrl: Ref<string>, options = {}) => {
        return useApiQuery({
            queryKey: boardQueryKeys.categories(boardUrl),
            request: () => boardApi.getCategories(boardUrl.value),
            enabled: computed(() => !!boardUrl.value),
            ...options
        })
    }

    // Create board mutation
    const useCreateBoard = () => {
        return useMutation({
            mutationFn: async (data: BoardCreateData) => {
                return unwrapAxiosApiData(await boardApi.createBoard(data))
            },
            onSuccess: () => {
                // Invalidate boards list and subscriptions to refresh header dropdowns
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    // Update board mutation
    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => {
                return unwrapAxiosApiData(await boardApi.updateBoard(boardUrl, data))
            },
            onSuccess: (updatedBoard, { boardUrl, data }) => {
                // Invalidate board details, boards list, and subscriptions
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
                const updatedBoardUrl = updatedBoard?.boardUrl ?? data.boardUrl
                if (updatedBoardUrl && updatedBoardUrl !== boardUrl) {
                    queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(updatedBoardUrl) })
                }
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useTransferBoardManager = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, loginId }: { boardUrl: string, loginId: string }) => {
                return unwrapAxiosApiData(await boardApi.updateBoardManager(boardUrl, { loginId }))
            },
            onSuccess: (_, { boardUrl }) => {
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useBoardManagerCandidates = (
        boardUrl: Ref<string>,
        params: Ref<BoardManagerCandidateParams>,
        enabled?: Ref<boolean>
    ) => {
        return useApiPageQuery<BoardManagerCandidate>({
            queryKey: boardQueryKeys.managerCandidates(boardUrl, params),
            request: () => boardApi.getBoardManagerCandidates(boardUrl.value, params.value),
            enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
        })
    }

    // Delete board mutation
    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: async (boardUrl: string) => {
                return unwrapAxiosApiData(await boardApi.deleteBoard(boardUrl))
            },
            onSuccess: () => {
                // Invalidate boards list and subscriptions to refresh header dropdowns
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    return {
        useBoards,
        useSubscribedBoards,
        useBoardDetail,
        useBoardPosts,
        useBoardNotices,
        useSubscribeBoard,
        useBoardCategories,
        useCreateBoard,
        useUpdateBoard,
        useTransferBoardManager,
        useBoardManagerCandidates,
        useDeleteBoard
    }
}
