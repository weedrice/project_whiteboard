import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useEventListener } from '@/composables/useEventListener'
import { isInputFocused } from '@/utils/keyboard'

interface ShortcutConfig {
  key: string
  ctrl?: boolean
  shift?: boolean
  alt?: boolean
  meta?: boolean
  handler: () => void
  description?: string
}

export function useGlobalShortcuts() {
  const shortcuts = ref<Map<string, ShortcutConfig>>(new Map())
  const router = useRouter()

  const createShortcutKey = (config: Omit<ShortcutConfig, 'handler' | 'description'>): string => {
    const parts: string[] = []
    if (config.ctrl) parts.push('ctrl')
    if (config.shift) parts.push('shift')
    if (config.alt) parts.push('alt')
    if (config.meta) parts.push('meta')
    parts.push(config.key.toLowerCase())
    return parts.join('+')
  }

  const handleKeyDown = (event: KeyboardEvent) => {
    if (typeof event.key !== 'string' || event.key.length === 0) {
      return
    }

    const alwaysActiveKeys = ['Escape']
    if (isInputFocused() && !alwaysActiveKeys.includes(event.key)) {
      return
    }

    const shortcutKey = createShortcutKey({
      key: event.key,
      ctrl: event.ctrlKey,
      shift: event.shiftKey,
      alt: event.altKey,
      meta: event.metaKey,
    })

    const shortcut = shortcuts.value.get(shortcutKey)
    if (shortcut) {
      event.preventDefault()
      shortcut.handler()
    }
  }

  const registerShortcut = (config: ShortcutConfig) => {
    const key = createShortcutKey(config)
    shortcuts.value.set(key, config)
  }

  const unregisterShortcut = (config: Omit<ShortcutConfig, 'handler' | 'description'>) => {
    const key = createShortcutKey(config)
    shortcuts.value.delete(key)
  }

  const clearShortcuts = () => {
    shortcuts.value.clear()
  }

  let gKeyPressed = false
  let gKeyTimer: ReturnType<typeof setTimeout> | null = null

  const clearGKeyTimer = () => {
    if (gKeyTimer) {
      clearTimeout(gKeyTimer)
      gKeyTimer = null
    }
  }

  const handleGKey = () => {
    if (!isInputFocused()) {
      gKeyPressed = true
      clearGKeyTimer()
      gKeyTimer = setTimeout(() => {
        gKeyPressed = false
      }, 1000)
    }
  }

  const handleNavigationKey = (route: string) => {
    if (gKeyPressed) {
      router.push(route)
      gKeyPressed = false
      clearGKeyTimer()
    }
  }

  const gKeyHandler = (event: KeyboardEvent) => {
    if (event.key === 'g' && !event.ctrlKey && !event.metaKey && !event.altKey && !event.shiftKey) {
      handleGKey()
      return
    }

    if (!gKeyPressed) {
      return
    }

    if (event.key === 'h' && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault()
      handleNavigationKey('/')
    } else if (event.key === 'b' && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault()
      handleNavigationKey('/boards')
    } else if (event.key === 'm' && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault()
      handleNavigationKey('/mypage')
    } else {
      gKeyPressed = false
      clearGKeyTimer()
    }
  }

  const documentTarget = () => typeof document === 'undefined' ? null : document

  useEventListener<KeyboardEvent>(documentTarget, 'keydown', handleKeyDown)
  useEventListener<KeyboardEvent>(documentTarget, 'keydown', gKeyHandler)

  onUnmounted(() => {
    clearGKeyTimer()
    clearShortcuts()
  })

  return {
    registerShortcut,
    unregisterShortcut,
    clearShortcuts,
  }
}
