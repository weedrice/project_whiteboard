import type { QueryClient } from '@tanstack/vue-query'
import { fetchBoardDetail } from '@/features/board/useBoard'
import { canWriteBoardPost } from '@/utils/board'
import type { BoardDetail } from '@/types'
import { createAuthSessionScope } from '@/utils/authSessionScope'
import { createAuthSessionChangedError } from '@/utils/authSessionIntent'
import { getAuthQuerySource } from '@/utils/authScopedQuery'

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

export async function fetchBoardForWriteAccess(
  _queryClient: QueryClient,
  boardUrl: string,
  sessionGeneration: number,
  signal?: AbortSignal,
): Promise<BoardDetail> {
  const source = getAuthQuerySource()
  if (source.sessionGeneration !== sessionGeneration || signal?.aborted) throw createAuthSessionChangedError()
  const scope = createAuthSessionScope(source)
  try {
    const board = await fetchBoardDetail(boardUrl, scope.withRequestConfig({ signal }))
    scope.assertCurrent()
    return board
  } finally {
    scope.dispose()
  }
}

export function canUserWriteBoardPost(
  board: BoardDetail | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null,
): boolean {
  return canWriteBoardPost(board, isAuthenticated, userRole)
}
