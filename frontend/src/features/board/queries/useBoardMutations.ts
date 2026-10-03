import { useQueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import type { AxiosRequestConfig } from 'axios'
import { unwrapAxiosApiData } from '@/api/response'
import { boardApi } from '@/api/board'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import {
  invalidateBoardListCaches,
  invalidateBoardResourceCaches,
  invalidateBoardSubscriptionCaches,
  removeBoardResourceCaches,
} from '@/features/board/queries/boardCacheInvalidation'
import type { BoardCreateData, BoardUpdateData } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey, isSessionGenerationCurrent } from '@/queryAuthScope'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'

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
    return useAuthScopedMutation({
      mutationFn: async ({ boardUrl, isSubscribed }: { boardUrl: string, isSubscribed: boolean }, mutationContext) => {
        if (isSubscribed) {
          if (requestConfig) {
            await boardApi.unsubscribeBoard(boardUrl, mutationContext.withRequestConfig(requestConfig))
            return
          }
          await boardApi.unsubscribeBoard(boardUrl, mutationContext.requestConfig)
        } else {
          if (requestConfig) {
            await boardApi.subscribeBoard(boardUrl, mutationContext.withRequestConfig(requestConfig))
            return
          }
          await boardApi.subscribeBoard(boardUrl, mutationContext.requestConfig)
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
    return useAuthScopedMutation({
      meta: LOCAL_MUTATION_ERROR_META,
      onMutate: captureMutationSession,
      mutationFn: async (request: BoardCreateData & { signal?: AbortSignal }, mutationContext) => {
        const { signal, ...data } = request
        return unwrapAxiosApiData(await boardApi.createBoard(data, mutationContext.withRequestConfig({
          signal,
          skipGlobalErrorHandler: true,
        })))
      },
      onSuccess: (_data, _variables, context) => {
        if (!isCurrentMutation(context)) return
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  const useUpdateBoard = () => {
    return useAuthScopedMutation({
      meta: LOCAL_MUTATION_ERROR_META,
      onMutate: captureMutationSession,
      mutationFn: async ({ boardUrl, data, signal }: {
        boardUrl: string
        data: BoardUpdateData
        signal?: AbortSignal
      }, mutationContext) => {
        return unwrapAxiosApiData(await boardApi.updateBoard(boardUrl, data, mutationContext.withRequestConfig({
          signal,
          skipGlobalErrorHandler: true,
        })))
      },
      onSuccess: (updatedBoard, { boardUrl, data }, context) => {
        if (!isCurrentMutation(context)) return
        const updatedBoardUrl = updatedBoard?.boardUrl ?? data.boardUrl
        if (updatedBoard && updatedBoardUrl) {
          queryClient.setQueryData(
            authKey(boardQueryKeys.detail(updatedBoardUrl)),
            updatedBoard,
          )
        }
        if (updatedBoardUrl && updatedBoardUrl !== boardUrl) {
          removeBoardResourceCaches(queryClient, boardUrl, context.sessionGeneration)
          invalidateBoardResourceCaches(queryClient, updatedBoardUrl, context.sessionGeneration)
        } else {
          invalidateBoardResourceCaches(queryClient, boardUrl, context.sessionGeneration)
        }
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  const useTransferBoardManager = () => {
    return useAuthScopedMutation({
      meta: LOCAL_MUTATION_ERROR_META,
      onMutate: captureMutationSession,
      mutationFn: async ({ boardUrl, loginId }: { boardUrl: string, loginId: string }, mutationContext) => {
        return unwrapAxiosApiData(await boardApi.updateBoardManager(
          boardUrl,
          { loginId },
          mutationContext.withRequestConfig({ skipGlobalErrorHandler: true }),
        ))
      },
      onSuccess: (_, { boardUrl }, context) => {
        if (!isCurrentMutation(context)) return
        queryClient.invalidateQueries({ queryKey: authKey(boardQueryKeys.detail(boardUrl)) })
        invalidateBoardListCaches(queryClient, authStore.sessionGeneration)
      },
    })
  }

  const useDeleteBoard = () => {
    return useAuthScopedMutation({
      meta: LOCAL_MUTATION_ERROR_META,
      onMutate: captureMutationSession,
      mutationFn: async (boardUrl: string, mutationContext) => {
        return unwrapAxiosApiData(await boardApi.deleteBoard(boardUrl, mutationContext.withRequestConfig({
          skipGlobalErrorHandler: true,
        })))
      },
      onSuccess: (_data, boardUrl, context) => {
        if (!isCurrentMutation(context)) return
        removeBoardResourceCaches(queryClient, boardUrl, context.sessionGeneration)
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
