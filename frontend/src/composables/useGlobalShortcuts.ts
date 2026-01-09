import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

interface ShortcutConfig {
  key: string
  ctrl?: boolean
  shift?: boolean
  alt?: boolean
  meta?: boolean
  handler: () => void
  description?: string
}

/**
 * 전역 키보드 단축키를 관리하는 composable
 * 
 * @example
 * ```typescript
 * const { registerShortcut, unregisterShortcut } = useGlobalShortcuts()
 * 
 * registerShortcut({
 *   key: '/',
 *   handler: () => focusSearchBar()
 * })
 * ```
 */
export function useGlobalShortcuts() {
  const shortcuts = ref<Map<string, ShortcutConfig>>(new Map())
  const router = useRouter()

  /**
   * 단축키 키 생성
   */
  const createShortcutKey = (config: Omit<ShortcutConfig, 'handler' | 'description'>): string => {
    const parts: string[] = []
    if (config.ctrl) parts.push('ctrl')
    if (config.shift) parts.push('shift')
    if (config.alt) parts.push('alt')
    if (config.meta) parts.push('meta')
    parts.push(config.key.toLowerCase())
    return parts.join('+')
  }

  /**
   * 키보드 이벤트 핸들러
   */
  const handleKeyDown = (event: KeyboardEvent) => {
    // 입력 필드에 포커스가 있으면 단축키 무시 (일부 예외)
    const target = event.target as HTMLElement
    const isInputFocused = target.tagName === 'INPUT' || 
                          target.tagName === 'TEXTAREA' || 
                          target.isContentEditable

    // 입력 필드에서도 작동해야 하는 단축키 (예: Escape, /)
    const alwaysActiveKeys = ['Escape', '/', '?']
    if (isInputFocused && !alwaysActiveKeys.includes(event.key)) {
      return
    }

    const shortcutKey = createShortcutKey({
      key: event.key,
      ctrl: event.ctrlKey,
      shift: event.shiftKey,
      alt: event.altKey,
      meta: event.metaKey
    })

    const shortcut = shortcuts.value.get(shortcutKey)
    if (shortcut) {
      event.preventDefault()
      shortcut.handler()
    }
  }

  /**
   * 단축키 등록
   */
  const registerShortcut = (config: ShortcutConfig) => {
    const key = createShortcutKey(config)
    shortcuts.value.set(key, config)
  }

  /**
   * 단축키 해제
   */
  const unregisterShortcut = (config: Omit<ShortcutConfig, 'handler' | 'description'>) => {
    const key = createShortcutKey(config)
    shortcuts.value.delete(key)
  }

  /**
   * 모든 단축키 해제
   */
  const clearShortcuts = () => {
    shortcuts.value.clear()
  }

  // 기본 단축키 등록
  const registerDefaultShortcuts = () => {
    // g + h: 홈으로 이동 (g 키 조합)
    let gKeyPressed = false
    let gKeyTimer: ReturnType<typeof setTimeout> | null = null

    const handleGKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement
      const isInputFocused = target.tagName === 'INPUT' || 
                            target.tagName === 'TEXTAREA' || 
                            target.isContentEditable
      if (!isInputFocused) {
        gKeyPressed = true
        if (gKeyTimer) clearTimeout(gKeyTimer)
        gKeyTimer = setTimeout(() => {
          gKeyPressed = false
        }, 1000) // 1초 내에 다음 키를 눌러야 함
      }
    }

    const handleNavigationKey = (route: string) => {
      if (gKeyPressed) {
        router.push(route)
        gKeyPressed = false
        if (gKeyTimer) {
          clearTimeout(gKeyTimer)
          gKeyTimer = null
        }
      }
    }

    // g 키 감지를 위한 별도 리스너
    const gKeyHandler = (e: KeyboardEvent) => {
      if (e.key === 'g' && !e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey) {
        handleGKey(e)
      } else if (gKeyPressed) {
        // g 키가 눌린 상태에서 다른 키 처리
        if (e.key === 'h' && !e.ctrlKey && !e.metaKey && !e.altKey) {
          e.preventDefault()
          handleNavigationKey('/')
        } else if (e.key === 'b' && !e.ctrlKey && !e.metaKey && !e.altKey) {
          e.preventDefault()
          handleNavigationKey('/boards')
        } else if (e.key === 'm' && !e.ctrlKey && !e.metaKey && !e.altKey) {
          e.preventDefault()
          handleNavigationKey('/mypage')
        } else {
          // 다른 키가 눌리면 g 키 상태 리셋
          gKeyPressed = false
          if (gKeyTimer) {
            clearTimeout(gKeyTimer)
            gKeyTimer = null
          }
        }
      }
    }

    document.addEventListener('keydown', gKeyHandler)

    // cleanup을 위해 저장
    onUnmounted(() => {
      document.removeEventListener('keydown', gKeyHandler)
    })
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeyDown)
    registerDefaultShortcuts()
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeyDown)
    clearShortcuts()
  })

  return {
    registerShortcut,
    unregisterShortcut,
    clearShortcuts
  }
}
