import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import { boardQueryKeys } from '@/composables/boardQueryKeys'
import { invalidateAdminBoardCaches } from '@/composables/adminCacheInvalidation'
import { invalidateBoardListCaches } from '@/composables/boardCacheInvalidation'
import {
    useAdminDataQuery,
    useAdminNullableDataQuery,
} from '@/composables/adminApiQuery'
import type {
    BoardCreateData,
    BoardManagerData,
    BoardUpdateData,
} from '@/composables/adminComposableTypes'
import type {
    AdminBoard,
    BoardAdminInfo,
} from '@/types'

export function useAdminBoardManagement(queryClient: QueryClient) {
    const useAdminBoards = () => {
        return useAdminDataQuery<AdminBoard[]>(adminQueryKeys.boards, () => adminApi.getBoards())
    }

    const useCreateBoard = () => {
        return useMutation({
            mutationFn: (data: BoardCreateData) => adminApi.createBoard(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => adminApi.updateBoard(boardUrl, data),
            onSuccess: (_, { boardUrl, data }) => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
                if (data.boardUrl && data.boardUrl !== boardUrl) {
                    queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(data.boardUrl) })
                }
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: (boardUrl: string) => adminApi.deleteBoard(boardUrl),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useBoardManager = (boardId: Ref<number | null>) => {
        const boardManagerQueryKey = adminQueryKeys.boardManager(boardId)
        const enabled = computed(() => boardId.value !== null)

        return useAdminNullableDataQuery<BoardAdminInfo | null>(
            boardManagerQueryKey,
            () => !boardId.value ? null : adminApi.getBoardManager(boardId.value),
            enabled
        )
    }

    const useUpdateBoardManager = () => {
        return useMutation({
            mutationFn: ({ boardId, data }: { boardId: number, data: BoardManagerData }) =>
                adminApi.updateBoardManager(boardId, data),
            onSuccess: (_, { boardId }) => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boardManagerById(boardId) })
                invalidateAdminBoardCaches(queryClient)
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.all })
            }
        })
    }

    return {
        useAdminBoards,
        useCreateBoard,
        useUpdateBoard,
        useDeleteBoard,
        useBoardManager,
        useUpdateBoardManager,
    }
}
