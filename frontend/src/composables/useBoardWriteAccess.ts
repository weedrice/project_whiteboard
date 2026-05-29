import type { QueryClient } from '@tanstack/vue-query'
import { createBoardDetailQueryOptions } from '@/composables/useBoard'
import { canWriteBoardPost } from '@/utils/board'
import type { BoardDetail } from '@/types'

interface VerifyBoardWriteAccessOptions {
  queryClient: QueryClient
  boardUrl: string
  isAuthenticated: boolean
  userRole?: string | null
}

export async function verifyBoardWriteAccess({
  queryClient,
  boardUrl,
  isAuthenticated,
  userRole,
}: VerifyBoardWriteAccessOptions): Promise<boolean> {
  const board = await queryClient.fetchQuery<BoardDetail>({
    ...createBoardDetailQueryOptions(boardUrl),
    retry: false,
  })

  return canUserWriteBoardPost(board, isAuthenticated, userRole)
}

export function canUserWriteBoardPost(
  board: BoardDetail | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null,
): boolean {
  return canWriteBoardPost(board, isAuthenticated, userRole)
}
