import { computed, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
import { useSearchSubmitNavigation } from '@/composables/useSearchSubmitNavigation'
import type { BoardListItem } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

interface UseSearchNavigationOptions {
  filteredBoards: Ref<BoardListItem[]>
  searchQuery: Ref<string>
  showDropdown: Ref<boolean>
  listboxId: string
  isMobile: { value: boolean }
  collapse: () => void
  focusSearchInput?: () => void
}

export function useSearchNavigation({
  filteredBoards,
  searchQuery,
  showDropdown,
  listboxId,
  isMobile,
  collapse,
  focusSearchInput,
}: UseSearchNavigationOptions) {
  const router = useRouter()
  const { submitSearch } = useSearchSubmitNavigation()

  const handleSearch = () => {
    const nextQuery = searchQuery.value.trim()
    if (!nextQuery) return

    showDropdown.value = false
    if (isMobile.value) collapse()

    submitSearch(nextQuery)
  }

  const selectBoard = (boardUrl: string) => {
    showDropdown.value = false
    searchQuery.value = ''
    if (isMobile.value) collapse()
    router.push(`/board/${encodePathSegment(boardUrl)}`)
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
        focusSearchInput?.()
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

  watch(searchQuery, () => {
    showDropdown.value = !!searchQuery.value.trim()
    resetSelection()
  })

  watch(filteredBoards, () => {
    resetSelection()
  })

  return {
    activeDescendantId,
    handleDropdownKeyDown,
    handleSearch,
    resetSelection,
    selectBoard,
    selectedIndex,
    setSelectedIndex,
  }
}
