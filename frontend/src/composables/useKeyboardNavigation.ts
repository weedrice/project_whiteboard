import { ref, onMounted, onUnmounted, type Ref } from 'vue'

/**
 * 리스트/드롭다운 키보드 네비게이션을 위한 composable
 * 
 * @param items 항목 배열
 * @param options 옵션 설정
 * @returns 네비게이션 상태 및 핸들러
 * 
 * @example
 * ```typescript
 * const { selectedIndex, handleKeyDown, setSelectedIndex } = useKeyboardNavigation(
 *   filteredBoards,
 *   {
 *     onSelect: (index) => selectBoard(filteredBoards.value[index].boardUrl),
 *     onEscape: () => closeDropdown()
 *   }
 * )
 * ```
 */
export function useKeyboardNavigation<T>(
  items: Ref<T[]>,
  options: {
    onSelect?: (index: number) => void
    onEscape?: () => void
    loop?: boolean // true면 마지막에서 다음으로 첫 번째로 이동
    initialIndex?: number
  } = {}
) {
  const { onSelect, onEscape, loop = true, initialIndex = -1 } = options

  const selectedIndex = ref(initialIndex)

  /**
   * 키보드 이벤트 핸들러
   */
  const handleKeyDown = (event: KeyboardEvent) => {
    const itemCount = items.value.length

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
        if (selectedIndex.value >= 0 && selectedIndex.value < itemCount && onSelect) {
          onSelect(selectedIndex.value)
        }
        break

      case 'Escape':
        event.preventDefault()
        if (onEscape) {
          onEscape()
        }
        break
    }
  }

  /**
   * 선택된 인덱스 설정
   */
  const setSelectedIndex = (index: number) => {
    if (index >= 0 && index < items.value.length) {
      selectedIndex.value = index
    }
  }

  /**
   * 선택된 인덱스 리셋
   */
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
