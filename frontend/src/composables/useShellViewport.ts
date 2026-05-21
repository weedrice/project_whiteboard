import { onMounted, onUnmounted, ref } from 'vue'

interface ShellViewportKeyboardStore {
  closeShortcutsModal: () => void
}

export function useShellViewport(keyboardStore: ShellViewportKeyboardStore) {
  const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 640)
  const isEditorFocused = ref(false)
  const mediaQuery = typeof window !== 'undefined' ? window.matchMedia('(max-width: 639px)') : null

  const updateIsMobile = () => {
    if (!mediaQuery) return

    isMobile.value = mediaQuery.matches
    if (mediaQuery.matches) {
      keyboardStore.closeShortcutsModal()
    }
  }

  const handleEditorFocusChange = (event: Event) => {
    const customEvent = event as CustomEvent<boolean>
    isEditorFocused.value = Boolean(customEvent.detail)
  }

  onMounted(() => {
    if (mediaQuery) {
      isMobile.value = mediaQuery.matches
      mediaQuery.addEventListener('change', updateIsMobile)
    }

    window.addEventListener('noviis:editor-focus-change', handleEditorFocusChange as EventListener)
  })

  onUnmounted(() => {
    if (mediaQuery) {
      mediaQuery.removeEventListener('change', updateIsMobile)
    }

    window.removeEventListener('noviis:editor-focus-change', handleEditorFocusChange as EventListener)
  })

  return {
    isMobile,
    isEditorFocused
  }
}
