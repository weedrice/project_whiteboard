import type { Ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'

/**
 * Keeps keyboard focus inside a container while it is active.
 * The caller controls activation with a ref or callback so the same utility can
 * be reused by dialogs, popovers, and menus.
 */
export function useFocusTrap(
  containerRef: Ref<HTMLElement | null>,
  isActive: Ref<boolean> | (() => boolean) = () => true,
) {
  let previouslyFocusedElement: HTMLElement | null = null

  const getFocusableElements = (): HTMLElement[] => {
    if (!containerRef.value) return []

    const selector = [
      'button:not([disabled])',
      '[href]',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ].join(', ')

    return Array.from(containerRef.value.querySelectorAll<HTMLElement>(selector))
      .filter((element) => {
        const style = window.getComputedStyle(element)
        return style.display !== 'none' && style.visibility !== 'hidden'
      })
  }

  const handleKeyDown = (event: KeyboardEvent) => {
    const isActiveValue = typeof isActive === 'function' ? isActive() : isActive.value
    if (!isActiveValue || event.key !== 'Tab') return

    const focusableElements = getFocusableElements()
    if (focusableElements.length === 0) return

    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]

    if (event.shiftKey) {
      if (document.activeElement === firstElement) {
        event.preventDefault()
        lastElement.focus()
      }
      return
    }

    if (document.activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }

  const trapFocus = () => {
    const focusableElements = getFocusableElements()
    if (focusableElements.length === 0) return

    previouslyFocusedElement = document.activeElement as HTMLElement
    focusableElements[0].focus()
  }

  const restoreFocus = () => {
    previouslyFocusedElement?.focus()
    previouslyFocusedElement = null
  }

  useEventListener(() => document, 'keydown', handleKeyDown)

  return {
    trapFocus,
    restoreFocus,
    getFocusableElements,
  }
}
