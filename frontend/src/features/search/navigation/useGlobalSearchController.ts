import { nextTick, ref, type Ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'
import { useMobileViewport } from '@/composables/useMediaQuery'
import { useSearchNavigation } from '@/features/search/navigation/useSearchNavigation'
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
  searchToggleRef: Ref<HTMLButtonElement | null>
  searchListboxId: string
}

export function useGlobalSearchController({
  filteredBoards,
  searchQuery,
  searchContainer,
  searchInputRef,
  searchToggleRef,
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

  const collapse = (restoreFocus = false) => {
    isExpanded.value = false
    showDropdown.value = false
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
    if (restoreFocus) {
      nextTick(() => searchToggleRef.value?.focus())
    }
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
    showDropdown.value = false
    resetSelection()
    if (isMobile.value && isExpanded.value) collapse()
  }

  const handleInputKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      if (isMobile.value && isExpanded.value) {
        collapse(true)
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

  useEventListener(() => document, 'click', handleClickOutside)

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
