import { useBoardMutations } from '@/composables/useBoardMutations'
import { useBoardQueries } from '@/composables/useBoardQueries'

export {
  boardDetailQueryKey,
  createBoardDetailQueryOptions,
  fetchBoardDetail,
  fetchBoardPosts,
} from '@/composables/useBoardQueries'
export type { BoardManagerCandidateParams, BoardPostParams } from '@/composables/useBoardQueries'

export function useBoard() {
  return {
    ...useBoardQueries(),
    ...useBoardMutations(),
  }
}
