import type { Ref } from 'vue'
import { useDialogLifecycle } from '@/composables/useDialogLifecycle'

export function usePopoverFocus(
  containerRef: Ref<HTMLElement | null>,
  isOpen: Ref<boolean>,
  close: () => void,
  returnFocusTarget?: () => HTMLElement | null,
) {
  return useDialogLifecycle({
    isOpen,
    dialogRef: containerRef,
    close,
    lockScroll: false,
    returnFocusTarget,
  })
}
