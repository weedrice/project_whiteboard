import type { QueryClient } from '@tanstack/vue-query'
import { fetchBoardDetail } from '@/features/board/useBoard'
import { canWriteBoardPost } from '@/utils/board'
import type { BoardDetail } from '@/types'

export const BOARD_WRITE_FORBIDDEN_MESSAGE_KEY = 'common.messages.boardWriteForbidden'
export const BOARD_WRITE_VERIFY_FAILED_MESSAGE_KEY = 'common.messages.loadFailed'

interface VerifyBoardWriteAccessOptions {
  queryClient: QueryClient
  boardUrl: string
  isAuthenticated: boolean
  userRole?: string | null
  sessionGeneration: number
}

export async function verifyBoardWriteAccess({
  queryClient,
  boardUrl,
  isAuthenticated,
  userRole,
  sessionGeneration,
}: VerifyBoardWriteAccessOptions): Promise<boolean> {
  const board = await fetchBoardForWriteAccess(queryClient, boardUrl, sessionGeneration)

  return canUserWriteBoardPost(board, isAuthenticated, userRole)
}

export function fetchBoardForWriteAccess(
  _queryClient: QueryClient,
  boardUrl: string,
  _sessionGeneration: number,
  signal?: AbortSignal,
): Promise<BoardDetail> {
  if (signal?.aborted) return Promise.reject(new DOMException('Request aborted', 'AbortError'))
  return fetchBoardDetail(boardUrl, signal ? { signal } : undefined)
}

export function canUserWriteBoardPost(
  board: BoardDetail | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null,
): boolean {
  return canWriteBoardPost(board, isAuthenticated, userRole)
}
