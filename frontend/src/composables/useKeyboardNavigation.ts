import { ref, type Ref } from 'vue'

/**
 * Keyboard navigation helper for list-like controls.
 */
export function useKeyboardNavigation<T>(
  items: Ref<T[]>,
  options: {
    onSelect?: (index: number) => void
    onEscape?: () => void
    loop?: boolean
    initialIndex?: number
    enableNumberKeys?: boolean
  } = {}
) {
  const { onSelect, onEscape, loop = true, initialIndex = -1, enableNumberKeys = false } = options

  const selectedIndex = ref(initialIndex)

  const handleKeyDown = (event: KeyboardEvent) => {
    const itemCount = items.value.length

    if (event.key === 'Escape') {
      event.preventDefault()
      onEscape?.()
      return
    }

    if (itemCount === 0) return

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        if (selectedIndex.value < itemCount - 1) {
          selectedIndex.value++
        } else if (loop) {
          selectedIndex.value = 0
        }
        break

      case 'ArrowUp':
        event.preventDefault()
        if (selectedIndex.value > 0) {
          selectedIndex.value--
        } else if (loop) {
          selectedIndex.value = itemCount - 1
        }
        break

      case 'Home':
        event.preventDefault()
        selectedIndex.value = 0
        break

      case 'End':
        event.preventDefault()
        selectedIndex.value = itemCount - 1
        break

      case 'Enter':
      case ' ':
        event.preventDefault()
        if (selectedIndex.value >= 0 && selectedIndex.value < itemCount) {
          onSelect?.(selectedIndex.value)
        }
        break

      default:
        if (enableNumberKeys && event.key >= '0' && event.key <= '9') {
          let index = -1
          if (event.key >= '1' && event.key <= '9') {
            index = Number.parseInt(event.key, 10) - 1
          } else if (event.key === '0') {
            index = 9
          }

          if (index >= 0 && index < itemCount) {
            event.preventDefault()
            onSelect?.(index)
          }
        }
        break
    }
  }

  const setSelectedIndex = (index: number) => {
    if (index >= 0 && index < items.value.length) {
      selectedIndex.value = index
    }
  }

  const reset = () => {
    selectedIndex.value = initialIndex
  }

  return {
    selectedIndex: selectedIndex as Readonly<Ref<number>>,
    handleKeyDown,
    setSelectedIndex,
    reset
  }
}
