import { watch, type Ref } from 'vue'
import { useRecentBoards } from '@/composables/useRecentBoards'
import type { BoardDetail } from '@/types'

export function useBoardRecentVisit(board: Ref<BoardDetail | null | undefined>) {
  const { addRecentBoard } = useRecentBoards()

  watch(board, (newBoard) => {
    if (!newBoard) return

    addRecentBoard({
      boardUrl: newBoard.boardUrl,
      boardName: newBoard.boardName,
      iconUrl: newBoard.iconUrl
    })
  }, { immediate: true })
}
