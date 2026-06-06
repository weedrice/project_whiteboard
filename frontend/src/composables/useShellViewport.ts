import { onMounted, onUnmounted, ref } from 'vue'
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

  onMounted(() => {
    window.addEventListener('noviis:editor-focus-change', handleEditorFocusChange as EventListener)
  })

  onUnmounted(() => {
    window.removeEventListener('noviis:editor-focus-change', handleEditorFocusChange as EventListener)
  })

  return {
    isMobile,
    isEditorFocused
  }
}
