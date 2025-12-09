import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { boardApi } from '@/api/board'
import { searchApi } from '@/api/search'
import { computed } from 'vue'

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
            staleTime: 1000 * 60 * 5, // 5 minutes
        })
    }

    // Fetch single board details
    const useBoardDetail = (boardUrl, options = {}) => {
        return useQuery({
            queryKey: ['board', boardUrl],
            queryFn: async () => {
                const { data } = await boardApi.getBoard(boardUrl.value)
                return data.data
            },
            enabled: computed(() => !!boardUrl.value),
            ...options
        })
    }

    // Fetch posts for a board (supports search)
    const useBoardPosts = (boardUrl, params, isSearching) => {
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
            enabled: computed(() => !!boardUrl.value),
            keepPreviousData: true,
        })
    }

    // Fetch notices for a board
    const useBoardNotices = (boardUrl) => {
        return useQuery({
            queryKey: ['board', boardUrl, 'notices'],
            queryFn: async () => {
                const { data } = await boardApi.getNotices(boardUrl.value)
                return data.data
            },
            enabled: computed(() => !!boardUrl.value),
        })
    }

    // Subscribe/Unsubscribe mutation
    const useSubscribeBoard = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, isSubscribed }) => {
                if (isSubscribed) {
                    await boardApi.unsubscribeBoard(boardUrl)
                } else {
                    await boardApi.subscribeBoard(boardUrl)
                }
            },
            onSuccess: (_, { boardUrl }) => {
                // Invalidate board details and boards list to refresh subscription status
                queryClient.invalidateQueries(['board', boardUrl])
                queryClient.invalidateQueries(['boards'])
            }
        })
    }

    // Fetch categories for a board
    const useBoardCategories = (boardUrl) => {
        return useQuery({
            queryKey: ['board', boardUrl, 'categories'],
            queryFn: async () => {
                const { data } = await boardApi.getCategories(boardUrl.value)
                return data.data
            },
            enabled: computed(() => !!boardUrl.value),
        })
    }

    return {
        useBoards,
        useBoardDetail,
        useBoardPosts,
        useBoardNotices,
        useSubscribeBoard,
        useBoardCategories
    }
}
