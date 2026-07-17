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
import { unwrapApiData } from '@/api/response'
import { useAuthStore } from '@/stores/auth'
import {
    captureSessionGeneration,
    isSessionGenerationCurrent,
    sessionQueryKey,
} from '@/queryAuthScope'

export function useAdminBoardManagement(queryClient: QueryClient) {
    const authStore = useAuthStore()
    const captureMutationSession = () => ({
        sessionGeneration: captureSessionGeneration(authStore),
    })
    const isCurrentMutation = (
        context?: { sessionGeneration: number },
    ): context is { sessionGeneration: number } =>
        context !== undefined
        && isSessionGenerationCurrent(authStore, context.sessionGeneration)

    const useAdminBoards = () => {
        return useAdminDataQuery<AdminBoard[]>(
            adminQueryKeys.boards,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getBoards, () => adminApi.getBoards()),
        )
    }

    const useCreateBoard = () => {
        return useMutation({
            mutationFn: (data: BoardCreateData) => adminApi.createBoard(data),
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminBoardListCaches(queryClient, context.sessionGeneration)
                invalidateBoardListCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => adminApi.updateBoard(boardUrl, data),
            onMutate: captureMutationSession,
            onSuccess: (_, { boardUrl, data }, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminBoardListCaches(queryClient, context.sessionGeneration)
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(context.sessionGeneration, boardQueryKeys.detail(boardUrl)),
                })
                if (data.boardUrl && data.boardUrl !== boardUrl) {
                    queryClient.invalidateQueries({
                        queryKey: sessionQueryKey(context.sessionGeneration, boardQueryKeys.detail(data.boardUrl)),
                    })
                }
                invalidateBoardListCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: (boardUrl: string) => adminApi.deleteBoard(boardUrl),
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminBoardListCaches(queryClient, context.sessionGeneration)
                invalidateBoardListCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useReorderBoards = () => {
        return useMutation({
            mutationFn: async (boardIds: number[]) => {
                const response = await adminApi.reorderBoards(boardIds)
                return unwrapApiData(response.data)
            },
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminBoardListCaches(queryClient, context.sessionGeneration)
                invalidateBoardListCaches(queryClient, context.sessionGeneration)
            },
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
            onMutate: captureMutationSession,
            onSuccess: (_, { boardId }, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminBoardManagerCache(queryClient, context.sessionGeneration, boardId)
                invalidateAdminBoardCaches(queryClient, context.sessionGeneration)
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(context.sessionGeneration, boardQueryKeys.all),
                })
            },
        })
    }

    return {
        useAdminBoards,
        useCreateBoard,
        useUpdateBoard,
        useDeleteBoard,
        useReorderBoards,
        useBoardManager,
        useUpdateBoardManager,
    }
}
