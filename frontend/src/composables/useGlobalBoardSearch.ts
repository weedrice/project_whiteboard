import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { useBoard } from '@/composables/useBoard'
import { useDebounce } from '@/composables/useDebounce'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
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
  const router = useRouter()
  const route = useRoute()
  const queryClient = useQueryClient()
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

  const selectBoard = (boardUrl: string) => {
    showDropdown.value = false
    searchQuery.value = ''
    if (isMobile.value) collapse()
    router.push(`/board/${boardUrl}`)
  }

  const {
    selectedIndex,
    handleKeyDown: handleDropdownKeyDown,
    reset: resetSelection,
    setSelectedIndex,
  } = useKeyboardNavigation(
    filteredBoards,
    {
      onSelect: (index) => {
        if (filteredBoards.value[index]) {
          selectBoard(filteredBoards.value[index].boardUrl)
        }
      },
      onEscape: () => {
        showDropdown.value = false
        focusSearchInput()
      },
      loop: true,
      initialIndex: -1,
    }
  )

  const activeDescendantId = computed(() => (
    selectedIndex.value >= 0 && filteredBoards.value[selectedIndex.value]
      ? `${listboxId}-${filteredBoards.value[selectedIndex.value].boardUrl}`
      : undefined
  ))

  const handleSearch = () => {
    const nextQuery = searchQuery.value.trim()
    if (!nextQuery) return

    showDropdown.value = false
    if (isMobile.value) collapse()

    if (route.name === 'search' && route.query.q === nextQuery) {
      queryClient.invalidateQueries({ queryKey: ['search', 'integrated'] })
      return
    }

    router.push({
      name: 'search',
      query: {
        q: nextQuery,
      },
    })
  }

  watch(searchQuery, () => {
    showDropdown.value = !!searchQuery.value.trim()
    resetSelection()
  })

  watch(filteredBoards, () => {
    resetSelection()
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
