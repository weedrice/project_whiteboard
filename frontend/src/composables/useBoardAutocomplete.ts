import { computed, type Ref } from 'vue'
import { useBoard } from '@/composables/useBoard'
import { useDebounce } from '@/composables/useDebounce'
import { DEBOUNCE_DELAY } from '@/utils/constants'
import type { BoardListItem } from '@/types'

export function useBoardAutocomplete(searchQuery: Ref<string>) {
  const { useBoards } = useBoard()
  const { data: boardsData } = useBoards()
  const debouncedSearchQuery = useDebounce(searchQuery, DEBOUNCE_DELAY.SEARCH)

  const boards = computed(() => boardsData.value || [])
  const filteredBoards = computed(() => {
    const query = debouncedSearchQuery.value.trim().toLowerCase()
    if (!query) return []
    return boards.value.filter((board: BoardListItem) =>
      board.boardName.toLowerCase().includes(query)
    )
  })

  return {
    boards,
    filteredBoards
  }
}
