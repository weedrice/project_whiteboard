import { nextTick, watch, type Ref } from 'vue'
import { useFocusTrap } from '@/composables/useFocusTrap'

export function usePopoverFocus(
  containerRef: Ref<HTMLElement | null>,
  isOpen: Ref<boolean>,
) {
  const { trapFocus, restoreFocus, getFocusableElements } = useFocusTrap(containerRef, isOpen)

  watch(
    isOpen,
    async (open) => {
      if (open) {
        await nextTick()
        trapFocus()
        return
      }
      restoreFocus()
    },
    { flush: 'post' },
  )

  return {
    trapFocus,
    restoreFocus,
    getFocusableElements,
  }
}
