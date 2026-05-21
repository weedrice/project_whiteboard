import type { Ref } from 'vue'
import type { DropdownType } from '@/stores/keyboard'
import { isInputFocused } from '@/utils/keyboard'

interface ShellShortcutAuthStore {
  isAuthenticated: boolean
  logout: () => Promise<void>
}

interface ShellShortcutKeyboardStore {
  isShortcutsModalOpen: boolean
  closeShortcutsModal: () => void
  toggleShortcutsModal: () => void
}

interface ShellShortcutRouter {
  push: (to: string) => Promise<unknown>
}

interface UseShellShortcutsOptions {
  authStore: ShellShortcutAuthStore
  keyboardStore: ShellShortcutKeyboardStore
  router: ShellShortcutRouter
  isMobile: Ref<boolean>
  hasOpenDropdown: () => boolean
  closeAllDropdowns: () => void
  setActiveDropdown: (name: Exclude<DropdownType, null>) => void
  toggleTheme: () => void
}

export function useShellShortcuts(options: UseShellShortcutsOptions) {
  const handleKeyDown = async (event: KeyboardEvent) => {
    const { key, shiftKey, ctrlKey, altKey, metaKey } = event

    if (key === 'Escape') {
      if (options.hasOpenDropdown()) {
        event.preventDefault()
        options.closeAllDropdowns()
        return
      }

      if (options.keyboardStore.isShortcutsModalOpen) {
        event.preventDefault()
        options.keyboardStore.closeShortcutsModal()
      }
      return
    }

    if (options.keyboardStore.isShortcutsModalOpen) return
    if (options.hasOpenDropdown()) return
    if (isInputFocused()) return

    if (ctrlKey || metaKey) {
      if (key === 'k' || key === 'K') {
        event.preventDefault()
        await options.router.push('/search')
      }
      return
    }

    if (altKey) {
      if ((key === 'n' || key === 'N') && options.authStore.isAuthenticated) {
        event.preventDefault()
        await options.router.push('/mypage/notifications')
      }
      return
    }

    if (shiftKey) {
      if (key === 'B') {
        event.preventDefault()
        await options.router.push('/boards')
        return
      }

      if ((key === '/' || key === '?') && !options.isMobile.value) {
        event.preventDefault()
        options.keyboardStore.toggleShortcutsModal()
      }
      return
    }

    switch (key) {
      case 's':
        if (options.authStore.isAuthenticated) {
          event.preventDefault()
          options.setActiveDropdown('subscription')
        }
        break
      case 'b':
        event.preventDefault()
        options.setActiveDropdown('all')
        break
      case 'h':
        event.preventDefault()
        await options.router.push('/')
        break
      case 'm':
        if (options.authStore.isAuthenticated) {
          event.preventDefault()
          options.setActiveDropdown('user')
        }
        break
      case 'd':
        event.preventDefault()
        options.toggleTheme()
        break
      case 'q':
        if (options.authStore.isAuthenticated) {
          event.preventDefault()
          await options.authStore.logout()
          await options.router.push('/')
        }
        break
    }
  }

  return {
    handleKeyDown
  }
}
