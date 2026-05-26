import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { useKeyboardNavigation } from '@/composables/useKeyboardNavigation'
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
  const router = useRouter()
  const route = useRoute()
  const queryClient = useQueryClient()

  const showDropdown = ref(false)
  const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 640)
  const isExpanded = ref(false)
  const mediaQuery = typeof window !== 'undefined' ? window.matchMedia('(max-width: 639px)') : null

  const updateIsMobile = () => {
    if (mediaQuery) {
      isMobile.value = mediaQuery.matches
      if (!mediaQuery.matches) isExpanded.value = false
    }
  }

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

  const handleSearch = () => {
    const nextQuery = searchQuery.value.trim()
    if (nextQuery) {
      showDropdown.value = false
      if (isMobile.value) collapse()

      if (route.name === 'search' && route.query.q === nextQuery) {
        queryClient.invalidateQueries({ queryKey: ['search', 'integrated'] })
        return
      }

      router.push({
        name: 'search',
        query: {
          q: nextQuery
        }
      })
    }
  }

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
    setSelectedIndex
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
      initialIndex: -1
    }
  )

  const activeDescendantId = computed(() => (
    selectedIndex.value >= 0 && filteredBoards.value[selectedIndex.value]
      ? `${searchListboxId}-${filteredBoards.value[selectedIndex.value].boardUrl}`
      : undefined
  ))

  watch(searchQuery, () => {
    showDropdown.value = !!searchQuery.value.trim()
    resetSelection()
  })

  watch(filteredBoards, () => {
    resetSelection()
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
    if (mediaQuery) {
      isMobile.value = mediaQuery.matches
      mediaQuery.addEventListener('change', updateIsMobile)
    }
    document.addEventListener('click', handleClickOutside)
  })

  onUnmounted(() => {
    if (mediaQuery) mediaQuery.removeEventListener('change', updateIsMobile)
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
