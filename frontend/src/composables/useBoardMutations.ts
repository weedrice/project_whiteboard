import { useMutation, useQueryClient } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'
import { unwrapAxiosApiData } from '@/api/response'
import { boardApi } from '@/api/board'
import { boardQueryKeys } from '@/composables/boardQueryKeys'
import { invalidateBoardListCaches } from '@/composables/boardCacheInvalidation'
import type { BoardCreateData, BoardUpdateData } from '@/types'

export function useBoardMutations() {
  const queryClient = useQueryClient()

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
        queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
        invalidateBoardListCaches(queryClient)
      },
      ...mutationOptions,
    })
  }

  const useCreateBoard = () => {
    return useMutation({
      mutationFn: async (data: BoardCreateData) => {
        return unwrapAxiosApiData(await boardApi.createBoard(data))
      },
      onSuccess: () => {
        invalidateBoardListCaches(queryClient)
      },
    })
  }

  const useUpdateBoard = () => {
    return useMutation({
      mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => {
        return unwrapAxiosApiData(await boardApi.updateBoard(boardUrl, data))
      },
      onSuccess: (updatedBoard, { boardUrl, data }) => {
        queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
        const updatedBoardUrl = updatedBoard?.boardUrl ?? data.boardUrl
        if (updatedBoardUrl && updatedBoardUrl !== boardUrl) {
          queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(updatedBoardUrl) })
        }
        invalidateBoardListCaches(queryClient)
      },
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
      },
    })
  }

  const useDeleteBoard = () => {
    return useMutation({
      mutationFn: async (boardUrl: string) => {
        return unwrapAxiosApiData(await boardApi.deleteBoard(boardUrl))
      },
      onSuccess: () => {
        invalidateBoardListCaches(queryClient)
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
