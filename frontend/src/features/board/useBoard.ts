import { useBoardMutations } from '@/features/board/queries/useBoardMutations'
import { useBoardQueries } from '@/features/board/queries/useBoardQueries'

export {
  boardDetailQueryKey,
  createBoardDetailQueryOptions,
  fetchBoardDetail,
  fetchBoardPosts,
} from '@/features/board/queries/useBoardQueries'
export type { BoardManagerCandidateParams, BoardPostParams } from '@/features/board/queries/useBoardQueries'

export function useBoard() {
  return {
    ...useBoardQueries(),
    ...useBoardMutations(),
  }
}
