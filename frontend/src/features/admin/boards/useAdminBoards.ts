import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import {
    invalidateAdminBoardCaches,
    invalidateAdminBoardListCaches,
    invalidateAdminBoardManagerCache,
} from '@/features/admin/queries/adminCacheInvalidation'
import { invalidateBoardListCaches } from '@/features/board/queries/boardCacheInvalidation'
import {
    callAdminApiWithOptionalConfig,
    useAdminDataQuery,
    useAdminNullableDataQuery,
} from '@/features/admin/queries/adminApiQuery'
import type { BoardManagerUpdateData } from '@/api/admin'
import type {
    AdminBoard,
    BoardAdminInfo,
    BoardCreateData,
    BoardUpdateData,
} from '@/types'

export function useAdminBoardManagement(queryClient: QueryClient) {
    const useAdminBoards = () => {
        return useAdminDataQuery<AdminBoard[]>(
            adminQueryKeys.boards,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getBoards, () => adminApi.getBoards()),
        )
    }

    const useCreateBoard = () => {
        return useMutation({
            mutationFn: (data: BoardCreateData) => adminApi.createBoard(data),
            onSuccess: () => {
                invalidateAdminBoardListCaches(queryClient)
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => adminApi.updateBoard(boardUrl, data),
            onSuccess: (_, { boardUrl, data }) => {
                invalidateAdminBoardListCaches(queryClient)
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
                invalidateAdminBoardListCaches(queryClient)
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useBoardManager = (boardId: Ref<number | null>) => {
        const boardManagerQueryKey = computed(() => adminQueryKeys.boardManager(boardId.value))
        const enabled = computed(() => boardId.value !== null)

        return useAdminNullableDataQuery<BoardAdminInfo | null>(
            boardManagerQueryKey,
            (config) => !boardId.value
                ? null
                : callAdminApiWithOptionalConfig(
                    config,
                    (requestConfig) => adminApi.getBoardManager(boardId.value as number, requestConfig),
                    () => adminApi.getBoardManager(boardId.value as number),
                ),
            enabled
        )
    }

    const useUpdateBoardManager = () => {
        return useMutation({
            mutationFn: ({ boardId, data }: { boardId: number, data: BoardManagerUpdateData }) =>
                adminApi.updateBoardManager(boardId, data),
            onSuccess: (_, { boardId }) => {
                invalidateAdminBoardManagerCache(queryClient, boardId)
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
