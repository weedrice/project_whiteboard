import type { QueryClient } from '@tanstack/vue-query'
import { createBoardDetailQueryOptions } from '@/features/board/useBoard'
import { canWriteBoardPost } from '@/utils/board'
import type { BoardDetail } from '@/types'

export const BOARD_WRITE_FORBIDDEN_MESSAGE_KEY = 'common.messages.boardWriteForbidden'
export const BOARD_WRITE_VERIFY_FAILED_MESSAGE_KEY = 'common.messages.loadFailed'

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
  const board = await fetchBoardForWriteAccess(queryClient, boardUrl)

  return canUserWriteBoardPost(board, isAuthenticated, userRole)
}

export function fetchBoardForWriteAccess(queryClient: QueryClient, boardUrl: string): Promise<BoardDetail> {
  return queryClient.fetchQuery<BoardDetail>({
    ...createBoardDetailQueryOptions(boardUrl),
    retry: false,
  })
}

export function canUserWriteBoardPost(
  board: BoardDetail | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null,
): boolean {
  return canWriteBoardPost(board, isAuthenticated, userRole)
}
