import { useMutation, useQueryClient } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'
import { unwrapAxiosApiData } from '@/api/response'
import { boardApi } from '@/api/board'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import {
  invalidateBoardListCaches,
  invalidateBoardSubscriptionCaches,
} from '@/features/board/queries/boardCacheInvalidation'
import type { BoardCreateData, BoardUpdateData } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey, isSessionGenerationCurrent } from '@/queryAuthScope'

export function useBoardMutations() {
  const queryClient = useQueryClient()
  const authStore = useAuthStore()
  const authKey = (queryKey: readonly unknown[]) => currentSessionQueryKey(authStore, queryKey)
  const captureMutationSession = () => ({ sessionGeneration: authStore.sessionGeneration })
  const isCurrentMutation = (context?: { sessionGeneration: number }) => (
    context !== undefined && isSessionGenerationCurrent(authStore, context.sessionGeneration)
  )

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
      onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
      onSuccess: (_, { boardUrl }, context) => {
        if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
        invalidateBoardSubscriptionCaches(queryClient, boardUrl, context.sessionGeneration)
      },
      ...mutationOptions,
    })
  }

  const useCreateBoard = () => {
    return useMutation({
      onMutate: captureMutationSession,
      mutationFn: async (data: BoardCreateData) => {
        return unwrapAxiosApiData(await boardApi.createBoard(data))
      },
      onSuccess: (_data, _variables, context) => {
        if (!isCurrentMutation(context)) return
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  const useUpdateBoard = () => {
    return useMutation({
      onMutate: captureMutationSession,
      mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => {
        return unwrapAxiosApiData(await boardApi.updateBoard(boardUrl, data))
      },
      onSuccess: (updatedBoard, { boardUrl, data }, context) => {
        if (!isCurrentMutation(context)) return
        queryClient.invalidateQueries({ queryKey: authKey(boardQueryKeys.detail(boardUrl)) })
        const updatedBoardUrl = updatedBoard?.boardUrl ?? data.boardUrl
        if (updatedBoardUrl && updatedBoardUrl !== boardUrl) {
          queryClient.invalidateQueries({ queryKey: authKey(boardQueryKeys.detail(updatedBoardUrl)) })
        }
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  const useTransferBoardManager = () => {
    return useMutation({
      onMutate: captureMutationSession,
      mutationFn: async ({ boardUrl, loginId }: { boardUrl: string, loginId: string }) => {
        return unwrapAxiosApiData(await boardApi.updateBoardManager(boardUrl, { loginId }))
      },
      onSuccess: (_, { boardUrl }, context) => {
        if (!isCurrentMutation(context)) return
        queryClient.invalidateQueries({ queryKey: authKey(boardQueryKeys.detail(boardUrl)) })
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  const useDeleteBoard = () => {
    return useMutation({
      onMutate: captureMutationSession,
      mutationFn: async (boardUrl: string) => {
        return unwrapAxiosApiData(await boardApi.deleteBoard(boardUrl))
      },
      onSuccess: (_data, _variables, context) => {
        if (!isCurrentMutation(context)) return
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  return {
    useSubscribeBoard,
    useCreateBoard,
    useUpdateBoard,
    useTransferBoardManager,
    useDeleteBoard,
  }
}
