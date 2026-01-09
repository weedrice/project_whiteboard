import { ref, watch, onUnmounted, type Ref } from 'vue'

/**
 * 포커스 트랩을 위한 composable
 * 모달이나 드롭다운 내에서 Tab 키로 포커스를 순환시킵니다.
 * 
 * @param containerRef 포커스를 트랩할 컨테이너 요소
 * @param isActive 활성 상태
 * @returns 포커스 트랩 핸들러
 * 
 * @example
 * ```typescript
 * const containerRef = ref<HTMLElement | null>(null)
 * const { trapFocus } = useFocusTrap(containerRef, isOpen)
 * 
 * watch(isOpen, (open) => {
 *   if (open) {
 *     trapFocus()
 *   }
 * })
 * ```
 */
export function useFocusTrap(
  containerRef: Ref<HTMLElement | null>,
  isActive: Ref<boolean> | (() => boolean) = () => true
) {
  let previouslyFocusedElement: HTMLElement | null = null

  /**
   * 포커스 가능한 요소들을 찾습니다
   */
  const getFocusableElements = (): HTMLElement[] => {
    if (!containerRef.value) return []

    const selector = [
      'button:not([disabled])',
      '[href]',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])'
    ].join(', ')

    return Array.from(containerRef.value.querySelectorAll<HTMLElement>(selector))
      .filter(el => {
        // 숨겨진 요소 제외
        const style = window.getComputedStyle(el)
        return style.display !== 'none' && style.visibility !== 'hidden'
      })
  }

  /**
   * 키보드 이벤트 핸들러
   */
  const handleKeyDown = (event: KeyboardEvent) => {
    const isActiveValue = typeof isActive === 'function' ? isActive() : isActive.value
    if (!isActiveValue || event.key !== 'Tab') return

    const focusableElements = getFocusableElements()
    if (focusableElements.length === 0) return

    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]

    if (event.shiftKey) {
      // Shift + Tab: 역방향
      if (document.activeElement === firstElement) {
        event.preventDefault()
        lastElement.focus()
      }
    } else {
      // Tab: 정방향
      if (document.activeElement === lastElement) {
        event.preventDefault()
        firstElement.focus()
      }
    }
  }

  /**
   * 포커스를 첫 번째 포커스 가능한 요소로 이동
   */
  const trapFocus = () => {
    const focusableElements = getFocusableElements()
    if (focusableElements.length > 0) {
      previouslyFocusedElement = document.activeElement as HTMLElement
      focusableElements[0].focus()
    }
  }

  /**
   * 이전 포커스로 복원
   */
  const restoreFocus = () => {
    if (previouslyFocusedElement) {
      previouslyFocusedElement.focus()
      previouslyFocusedElement = null
    }
  }

  // 키보드 이벤트 리스너 등록
  onMounted(() => {
    document.addEventListener('keydown', handleKeyDown)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeyDown)
  })

  return {
    trapFocus,
    restoreFocus,
    getFocusableElements
  }
}
