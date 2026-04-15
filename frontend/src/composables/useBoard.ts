import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { boardApi } from '@/api/board'
import type { BoardCreateData, BoardUpdateData } from '@/types'
import { userApi } from '@/api/user'
import { searchApi } from '@/api/search'
import { computed, type Ref } from 'vue'
import type { PageResponse, PostSummary, Board } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'

interface BoardPostParams {
    page?: number
    size?: number
    categoryId?: number
    sort?: string
    q?: string
    searchType?: string
}

export function useBoard() {
    const queryClient = useQueryClient()

    // Fetch all boards
    const useBoards = () => {
        return useQuery({
            queryKey: ['boards'],
            queryFn: async () => {
                const { data } = await boardApi.getBoards()
                return data.data
            },
            staleTime: QUERY_STALE_TIME.MEDIUM, // 5 minutes
        })
    }

    // Fetch subscribed boards
    const useSubscribedBoards = (size: number = 10, enabled?: Ref<boolean> | boolean) => {
        const enabledValue = enabled !== undefined
            ? (typeof enabled === 'boolean' ? computed(() => enabled) : enabled)
            : computed(() => false)
        return useQuery({
            queryKey: ['boards', 'subscriptions', size],
            queryFn: async () => {
                const { data } = await userApi.getMySubscriptions({ size })
                return data.data.content as Board[]
            },
            staleTime: QUERY_STALE_TIME.MEDIUM, // 5 minutes
            enabled: enabledValue,
        })
    }

    // Fetch single board details
    const useBoardDetail = (boardUrl: Ref<string>, options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}) => {
        const { requestConfig, ...queryOptions } = options
        return useQuery({
            queryKey: ['board', boardUrl],
            queryFn: async () => {
                const { data } = await boardApi.getBoard(boardUrl.value, requestConfig)
                return data.data
            },
            enabled: computed(() => !!boardUrl.value),
            ...queryOptions
        })
    }

    // Fetch posts for a board (supports search)
    const useBoardPosts = (boardUrl: Ref<string>, params: Ref<BoardPostParams>, isSearching?: Ref<boolean>, enabled?: Ref<boolean>) => {
        return useQuery({
            queryKey: ['board', boardUrl, 'posts', params, isSearching],
            queryFn: async () => {
                if (isSearching?.value) {
                    const { data } = await searchApi.searchPosts({ ...params.value, boardUrl: boardUrl.value })
                    return data.data
                } else {
                    const { data } = await boardApi.getPosts(boardUrl.value, params.value)
                    return data.data
                }
            },
            enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
            placeholderData: (previousData) => previousData,
        })
    }

    // Fetch notices for a board
    const useBoardNotices = (boardUrl: Ref<string>, enabled?: Ref<boolean>) => {
        return useQuery({
            queryKey: ['board', boardUrl, 'notices'],
            queryFn: async () => {
                const { data } = await boardApi.getNotices(boardUrl.value)
                return data.data
            },
            enabled: computed(() => !!boardUrl.value && (enabled?.value ?? true)),
        })
    }

    // Subscribe/Unsubscribe mutation
    const useSubscribeBoard = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, isSubscribed }: { boardUrl: string, isSubscribed: boolean }) => {
                if (isSubscribed) {
                    await boardApi.unsubscribeBoard(boardUrl)
                } else {
                    await boardApi.subscribeBoard(boardUrl)
                }
            },
            onSuccess: (_, { boardUrl }) => {
                // Invalidate board details and boards list to refresh subscription status
                queryClient.invalidateQueries({ queryKey: ['board', boardUrl] })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    // Fetch categories for a board
    const useBoardCategories = (boardUrl: Ref<string>, options = {}) => {
        return useQuery({
            queryKey: ['board', boardUrl, 'categories'],
            queryFn: async () => {
                const { data } = await boardApi.getCategories(boardUrl.value)
                return data.data
            },
            enabled: computed(() => !!boardUrl.value),
            ...options
        })
    }

    // Create board mutation
    const useCreateBoard = () => {
        return useMutation({
            mutationFn: async (data: BoardCreateData) => {
                const { data: response } = await boardApi.createBoard(data)
                return response.data
            },
            onSuccess: () => {
                // Invalidate boards list and subscriptions to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    // Update board mutation
    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => {
                const { data: response } = await boardApi.updateBoard(boardUrl, data)
                return response.data
            },
            onSuccess: (_, { boardUrl }) => {
                // Invalidate board details, boards list, and subscriptions
                queryClient.invalidateQueries({ queryKey: ['board', boardUrl] })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    const useTransferBoardManager = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, loginId }: { boardUrl: string, loginId: string }) => {
                const { data: response } = await boardApi.updateBoardManager(boardUrl, { loginId })
                return response.data
            },
            onSuccess: (_, { boardUrl }) => {
                queryClient.invalidateQueries({ queryKey: ['board', boardUrl] })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    // Delete board mutation
    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: async (boardUrl: string) => {
                const { data: response } = await boardApi.deleteBoard(boardUrl)
                return response.data
            },
            onSuccess: () => {
                // Invalidate boards list and subscriptions to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
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
        useDeleteBoard
    }
}
