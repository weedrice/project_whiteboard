import { computed, ref } from 'vue'
import { useBoard } from '@/composables/useBoard'
import { useDebounce } from '@/composables/useDebounce'
import { useSearchNavigation } from '@/composables/useSearchNavigation'
import { DEBOUNCE_DELAY } from '@/utils/constants'
import type { BoardListItem } from '@/types'

interface UseGlobalBoardSearchOptions {
  listboxId: string
  isMobile: { value: boolean }
  collapse: () => void
  focusSearchInput: () => void
}

export function useGlobalBoardSearch({
  listboxId,
  isMobile,
  collapse,
  focusSearchInput,
}: UseGlobalBoardSearchOptions) {
  const { useBoards } = useBoard()
  const { data: boardsData } = useBoards()

  const searchQuery = ref('')
  const debouncedSearchQuery = useDebounce(searchQuery, DEBOUNCE_DELAY.SEARCH)
  const showDropdown = ref(false)

  const boards = computed(() => boardsData.value || [])
  const filteredBoards = computed(() => {
    if (!debouncedSearchQuery.value.trim()) return []
    const query = debouncedSearchQuery.value.toLowerCase()
    return boards.value.filter((board: BoardListItem) =>
      board.boardName.toLowerCase().includes(query)
    )
  })

  const {
    activeDescendantId,
    selectedIndex,
    reset: resetSelection,
    handleDropdownKeyDown,
    handleSearch,
    selectBoard,
    setSelectedIndex,
  } = useSearchNavigation({
    filteredBoards,
    searchQuery,
    showDropdown,
    listboxId,
    isMobile,
    collapse,
    focusSearchInput,
  })

  return {
    searchQuery,
    showDropdown,
    filteredBoards,
    selectedIndex,
    activeDescendantId,
    setSelectedIndex,
    handleDropdownKeyDown,
    handleSearch,
    selectBoard,
    resetSelection,
  }
}
