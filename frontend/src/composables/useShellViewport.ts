import { ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'
import { useMobileViewport } from '@/composables/useMediaQuery'

interface ShellViewportKeyboardStore {
  closeShortcutsModal: () => void
}

export function useShellViewport(keyboardStore: ShellViewportKeyboardStore) {
  const isMobile = useMobileViewport((matches) => {
    if (matches) {
      keyboardStore.closeShortcutsModal()
    }
  })
  const isEditorFocused = ref(false)

  const handleEditorFocusChange = (event: Event) => {
    const customEvent = event as CustomEvent<boolean>
    isEditorFocused.value = Boolean(customEvent.detail)
  }

  useEventListener(() => window, 'noviis:editor-focus-change', handleEditorFocusChange)

  return {
    isMobile,
    isEditorFocused
  }
}
