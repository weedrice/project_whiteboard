import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboardStore } from '@/stores/keyboard'
import { useAuthStore } from '@/stores/auth'
import { useThemePreference } from '@/composables/useThemePreference'
import { isInputFocused } from '@/utils/keyboard'

export interface KeyboardShortcutHandlers {
    toggleSubscriptionDropdown?: () => void
    toggleAllBoardsDropdown?: () => void
    toggleUserDropdown?: () => void
    toggleNotificationDropdown?: () => void
    logout?: () => void | Promise<void>
    isDropdownOpen?: () => boolean
    closeCurrentDropdown?: () => void
}

export function useKeyboardShortcuts(handlers: KeyboardShortcutHandlers = {}) {
    const router = useRouter()
    const keyboardStore = useKeyboardStore()
    const authStore = useAuthStore()
    const { toggleTheme } = useThemePreference()

    const handleKeyDown = (event: KeyboardEvent) => {
        const { key, shiftKey, ctrlKey, altKey, metaKey } = event
        const isDropdownOpen = keyboardStore.isDropdownOpen || Boolean(handlers.isDropdownOpen?.())

        if (isDropdownOpen) {
            if (key === 'Escape') {
                event.preventDefault()
                handlers.closeCurrentDropdown?.()
                keyboardStore.closeDropdown()
            }
            return
        }

        if (key === 'Escape' && keyboardStore.isShortcutsModalOpen) {
            event.preventDefault()
            keyboardStore.closeShortcutsModal()
            return
        }

        if (keyboardStore.isShortcutsModalOpen) return

        if (isInputFocused()) {
            return
        }

        if (ctrlKey || metaKey) {
            if (key === 'k' || key === 'K') {
                event.preventDefault()
                router.push('/search')
            }
            return
        }

        if (altKey) {
            if ((key === 'n' || key === 'N') && authStore.isAuthenticated) {
                event.preventDefault()
                router.push('/mypage/notifications')
            }
            return
        }

        if (shiftKey) {
            if (key === 'B') {
                event.preventDefault()
                router.push('/boards')
                return
            }

            if ((key === '/' || key === '?') && window.innerWidth >= 640) {
                event.preventDefault()
                keyboardStore.toggleShortcutsModal()
            }
            return
        }

        switch (key) {
            case 's':
            case 'S':
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    handlers.toggleSubscriptionDropdown?.()
                }
                break
            case 'b':
                event.preventDefault()
                handlers.toggleAllBoardsDropdown?.()
                break
            case 'h':
                event.preventDefault()
                router.push('/')
                break
            case 'm':
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    handlers.toggleUserDropdown?.()
                }
                break
            case 'd':
                event.preventDefault()
                toggleTheme()
                break
            case 'q':
                if (authStore.isAuthenticated) {
                    event.preventDefault()
                    void handlers.logout?.()
                }
                break
        }
    }

    onMounted(() => {
        document.addEventListener('keydown', handleKeyDown)
    })

    onUnmounted(() => {
        document.removeEventListener('keydown', handleKeyDown)
    })

    return {
        keyboardStore,
        isInputFocused,
    }
}

export interface BoardDetailShortcutHandlers {
    goToNextPage: () => void
    goToPrevPage: () => void
    goToFirstPage: () => void
    goToLastPage: () => void
    goToWrite: () => void
    toggleSubscribe: () => void
    focusSearch: () => void
    canWrite: boolean | (() => boolean)
    canGoNext: boolean | (() => boolean)
    canGoPrev: boolean | (() => boolean)
    canToggleSubscribe?: boolean | (() => boolean)
    shouldIgnoreShortcut?: () => boolean
}

export function useBoardDetailShortcuts(handlers: BoardDetailShortcutHandlers) {
    const authStore = useAuthStore()

    const resolveGuard = (guard: boolean | (() => boolean) | undefined, defaultValue = false): boolean => {
        if (typeof guard === 'function') {
            return guard()
        }
        return guard ?? defaultValue
    }

    const handleKeyDown = (event: KeyboardEvent) => {
        const { key, shiftKey, ctrlKey, altKey, metaKey } = event

        if (ctrlKey || altKey || metaKey) return
        if (isInputFocused()) return
        if (handlers.shouldIgnoreShortcut?.()) return

        if (shiftKey) {
            if (key === '[' || key === '{') {
                event.preventDefault()
                handlers.goToFirstPage()
                return
            }
            if (key === ']' || key === '}') {
                event.preventDefault()
                handlers.goToLastPage()
            }
            return
        }

        switch (key) {
            case ']':
                if (resolveGuard(handlers.canGoNext)) {
                    event.preventDefault()
                    handlers.goToNextPage()
                }
                break
            case '[':
                if (resolveGuard(handlers.canGoPrev)) {
                    event.preventDefault()
                    handlers.goToPrevPage()
                }
                break
            case 'n':
            case 'N':
                if (resolveGuard(handlers.canWrite)) {
                    event.preventDefault()
                    handlers.goToWrite()
                }
                break
            case 'f':
            case 'F':
                if (authStore.isAuthenticated && resolveGuard(handlers.canToggleSubscribe, true)) {
                    event.preventDefault()
                    handlers.toggleSubscribe()
                }
                break
            case '/':
                event.preventDefault()
                handlers.focusSearch()
                break
        }
    }

    onMounted(() => {
        document.addEventListener('keydown', handleKeyDown)
    })

    onUnmounted(() => {
        document.removeEventListener('keydown', handleKeyDown)
    })
}
