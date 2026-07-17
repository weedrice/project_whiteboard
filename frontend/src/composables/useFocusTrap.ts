import type { Ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'

/** Keeps keyboard and programmatic focus inside a container while active. */
export function useFocusTrap(
  containerRef: Ref<HTMLElement | null>,
  isActive: Ref<boolean> | (() => boolean) = () => true,
) {
  let previouslyFocusedElement: HTMLElement | null = null
  let addedContainerTabIndex = false
  let restoringFocus = false
  const active = () => typeof isActive === 'function' ? isActive() : isActive.value

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

  const focusInside = (rememberReturnFocus: boolean) => {
    const container = containerRef.value
    if (!container) return
    const focusable = getFocusableElements()
    if (focusable.length === 0 && !container.hasAttribute('tabindex')) {
      container.setAttribute('tabindex', '-1')
      addedContainerTabIndex = true
    }
    const focusTarget = focusable[0] ?? container
    if (rememberReturnFocus && previouslyFocusedElement === null) {
      previouslyFocusedElement = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null
    }
    focusTarget.focus()
  }

  const trapFocus = () => focusInside(true)

  const handleKeyDown = (event: KeyboardEvent) => {
    if (!active() || event.key !== 'Tab' || !containerRef.value) return
    const focusable = getFocusableElements()
    if (focusable.length === 0) {
      event.preventDefault()
      containerRef.value.focus()
      return
    }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const focusIsOutside = !containerRef.value.contains(document.activeElement)
    if (event.shiftKey && (focusIsOutside || document.activeElement === first)) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && (focusIsOutside || document.activeElement === last)) {
      event.preventDefault()
      first.focus()
    }
  }

  const handleFocusIn = (event: FocusEvent) => {
    if (restoringFocus || !active() || !containerRef.value || containerRef.value.contains(event.target as Node)) return
    focusInside(false)
  }

  const restoreFocus = () => {
    const returnTarget = previouslyFocusedElement
    previouslyFocusedElement = null
    if (addedContainerTabIndex && containerRef.value) {
      containerRef.value.removeAttribute('tabindex')
      addedContainerTabIndex = false
    }
    restoringFocus = true
    try {
      returnTarget?.focus()
    } finally {
      restoringFocus = false
    }
  }

  useEventListener(() => document, 'keydown', handleKeyDown)
  useEventListener(() => document, 'focusin', handleFocusIn)

  return { trapFocus, restoreFocus, getFocusableElements }
}
