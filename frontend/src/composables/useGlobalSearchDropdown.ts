import { nextTick, onMounted, onUnmounted, ref, type Ref } from 'vue'

interface UseGlobalSearchDropdownOptions {
  searchContainer: Ref<HTMLElement | null>
  searchInputRef: Ref<unknown>
  closeBoardDropdown: () => void
  resetSelection: () => void
}

export function useGlobalSearchDropdown({
  searchContainer,
  searchInputRef,
  closeBoardDropdown,
  resetSelection,
}: UseGlobalSearchDropdownOptions) {
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
    const comp = searchInputRef.value as { $el?: HTMLElement; focus?: () => void } | HTMLInputElement | null
    if (comp instanceof HTMLInputElement) {
      comp.focus()
      return
    }
    if (typeof comp?.focus === 'function') {
      comp.focus()
      return
    }
    const input = comp?.$el?.querySelector?.('input')
    if (input instanceof HTMLInputElement) input.focus()
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
    closeBoardDropdown()
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  }

  const handleClickOutside = (event: Event) => {
    if (!searchContainer.value || searchContainer.value.contains(event.target as Node)) return
    if (isMobile.value && isExpanded.value && document.activeElement && searchContainer.value.contains(document.activeElement)) return
    closeBoardDropdown()
    resetSelection()
    if (isMobile.value && isExpanded.value) collapse()
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
    isMobile,
    isExpanded,
    focusSearchInput,
    expandAndFocus,
    collapse,
  }
}
