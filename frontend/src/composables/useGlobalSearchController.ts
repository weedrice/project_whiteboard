import { nextTick, onMounted, onUnmounted, ref, type Ref } from 'vue'
import { useMobileViewport } from '@/composables/useMediaQuery'
import { useSearchNavigation } from '@/composables/useSearchNavigation'
import type { BoardListItem } from '@/types'

interface SearchInputLike {
  $el?: HTMLElement
  focus?: () => void
}

interface UseGlobalSearchControllerOptions {
  filteredBoards: Ref<BoardListItem[]>
  searchQuery: Ref<string>
  searchContainer: Ref<HTMLElement | null>
  searchInputRef: Ref<SearchInputLike | null>
  searchListboxId: string
}

export function useGlobalSearchController({
  filteredBoards,
  searchQuery,
  searchContainer,
  searchInputRef,
  searchListboxId
}: UseGlobalSearchControllerOptions) {
  const showDropdown = ref(false)
  const isExpanded = ref(false)
  const isMobile = useMobileViewport((matches) => {
    if (!matches) {
      isExpanded.value = false
    }
  })

  const focusSearchInput = () => {
    const refValue = searchInputRef.value
    if (!refValue) return

    const directInput = refValue instanceof HTMLInputElement ? refValue : null
    const nestedInput = refValue.$el?.querySelector?.('input')
    const input = directInput ?? (nestedInput instanceof HTMLInputElement ? nestedInput : null)

    if (input) {
      input.focus()
      return
    }

    refValue.focus?.()
  }

  const expandAndFocus = () => {
    isExpanded.value = true
    nextTick(() => {
      nextTick(() => {
        requestAnimationFrame(() => focusSearchInput())
      })
    })
  }

  const collapse = () => {
    isExpanded.value = false
    showDropdown.value = false
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  }

  const {
    activeDescendantId,
    selectedIndex,
    handleDropdownKeyDown,
    handleSearch,
    resetSelection,
    selectBoard,
    setSelectedIndex,
  } = useSearchNavigation({
    filteredBoards,
    searchQuery,
    showDropdown,
    listboxId: searchListboxId,
    isMobile,
    collapse,
    focusSearchInput,
  })

  const handleClickOutside = (event: Event) => {
    if (!searchContainer.value || searchContainer.value.contains(event.target as Node)) return
    if (isMobile.value && isExpanded.value && document.activeElement && searchContainer.value.contains(document.activeElement)) return
    showDropdown.value = false
    resetSelection()
    if (isMobile.value && isExpanded.value) collapse()
  }

  const handleInputKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      if (isMobile.value && isExpanded.value) {
        collapse()
        return
      }
      showDropdown.value = false
      resetSelection()
      if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
      return
    }

    if (showDropdown.value && filteredBoards.value.length > 0) {
      if (event.key === 'Enter' && selectedIndex.value === -1) {
        handleSearch()
        return
      }
      handleDropdownKeyDown(event)
    } else if (event.key === 'Enter') {
      handleSearch()
    }
  }

  onMounted(() => {
    document.addEventListener('click', handleClickOutside)
  })

  onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside)
  })

  return {
    activeDescendantId,
    collapse,
    expandAndFocus,
    handleInputKeyDown,
    handleSearch,
    isExpanded,
    isMobile,
    searchQuery,
    selectBoard,
    selectedIndex,
    setSelectedIndex,
    showDropdown,
  }
}
