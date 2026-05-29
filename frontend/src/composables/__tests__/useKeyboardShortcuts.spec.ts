import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
    useBoardDetailShortcuts,
    useKeyboardShortcuts,
    type KeyboardShortcutHandlers,
} from '../useKeyboardShortcuts'

const routerPush = vi.hoisted(() => vi.fn())
const toggleTheme = vi.hoisted(() => vi.fn())
const authState = vi.hoisted(() => ({ isAuthenticated: false }))
const keyboardState = vi.hoisted(() => ({
    isDropdownOpen: false,
    isShortcutsModalOpen: false,
    closeDropdown: vi.fn(),
    closeShortcutsModal: vi.fn(),
    toggleShortcutsModal: vi.fn(),
}))

vi.mock('vue-router', () => ({
    useRouter: () => ({ push: routerPush }),
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => authState,
}))

vi.mock('@/stores/keyboard', () => ({
    useKeyboardStore: () => keyboardState,
}))

vi.mock('@/composables/useThemePreference', () => ({
    useThemePreference: () => ({ toggleTheme }),
}))

vi.mock('@/utils/keyboard', () => ({
    isInputFocused: () => false,
}))

const mountShortcuts = (handlers: KeyboardShortcutHandlers = {}) => {
    return mount(defineComponent({
        setup() {
            useKeyboardShortcuts(handlers)
            return () => h('div')
        },
    }))
}

function mountBoardShortcuts(overrides: Partial<Parameters<typeof useBoardDetailShortcuts>[0]> = {}) {
    const handlers = {
        goToNextPage: vi.fn(),
        goToPrevPage: vi.fn(),
        goToFirstPage: vi.fn(),
        goToLastPage: vi.fn(),
        goToWrite: vi.fn(),
        toggleSubscribe: vi.fn(),
        canWrite: () => true,
        canGoNext: () => true,
        canGoPrev: () => true,
        ...overrides,
    }

    const Host = defineComponent({
        setup() {
            useBoardDetailShortcuts(handlers)
            return () => null
        },
    })

    return {
        handlers,
        wrapper: mount(Host),
    }
}

let wrapper: VueWrapper | undefined

const dispatchKey = (key: string, options: KeyboardEventInit = {}) => {
    const event = new KeyboardEvent('keydown', {
        key,
        bubbles: true,
        cancelable: true,
        ...options,
    })
    document.dispatchEvent(event)
    return event
}

describe('useKeyboardShortcuts', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        authState.isAuthenticated = false
        keyboardState.isDropdownOpen = false
        keyboardState.isShortcutsModalOpen = false
        Object.defineProperty(window, 'innerWidth', {
            configurable: true,
            value: 1024,
        })
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    it('keeps search on Ctrl/Cmd+K and ignores plain k', () => {
        wrapper = mountShortcuts()

        dispatchKey('k')
        expect(routerPush).not.toHaveBeenCalled()

        const event = dispatchKey('k', { ctrlKey: true })
        expect(event.defaultPrevented).toBe(true)
        expect(routerPush).toHaveBeenCalledWith('/search')
    })

    it('opens shortcut help only through shifted help key on desktop', () => {
        wrapper = mountShortcuts()

        dispatchKey('?')
        expect(keyboardState.toggleShortcutsModal).not.toHaveBeenCalled()

        const event = dispatchKey('/', { shiftKey: true })
        expect(event.defaultPrevented).toBe(true)
        expect(keyboardState.toggleShortcutsModal).toHaveBeenCalledTimes(1)
    })

    it('blocks non-Escape shortcuts while shortcuts modal is open', () => {
        keyboardState.isShortcutsModalOpen = true
        wrapper = mountShortcuts()

        dispatchKey('h')
        expect(routerPush).not.toHaveBeenCalled()

        const event = dispatchKey('Escape')
        expect(event.defaultPrevented).toBe(true)
        expect(keyboardState.closeShortcutsModal).toHaveBeenCalledTimes(1)
    })

    it('delegates authenticated logout shortcut to the layout handler', () => {
        authState.isAuthenticated = true
        const logout = vi.fn()
        wrapper = mountShortcuts({ logout })

        const event = dispatchKey('q')

        expect(event.defaultPrevented).toBe(true)
        expect(logout).toHaveBeenCalledTimes(1)
    })

    it('closes active layout dropdowns and ignores other shortcuts while open', () => {
        const closeCurrentDropdown = vi.fn()
        wrapper = mountShortcuts({
            isDropdownOpen: () => true,
            closeCurrentDropdown,
        })

        dispatchKey('h')
        expect(routerPush).not.toHaveBeenCalled()

        const event = dispatchKey('Escape')
        expect(event.defaultPrevented).toBe(true)
        expect(closeCurrentDropdown).toHaveBeenCalledTimes(1)
        expect(keyboardState.closeDropdown).toHaveBeenCalledTimes(1)
    })
})

describe('useBoardDetailShortcuts', () => {
    beforeEach(() => {
        authState.isAuthenticated = true
    })

    it('routes board navigation keys through function guards', async () => {
        const { handlers, wrapper: boardWrapper } = mountBoardShortcuts()
        await nextTick()

        document.dispatchEvent(new KeyboardEvent('keydown', { key: ']' }))
        document.dispatchEvent(new KeyboardEvent('keydown', { key: '[' }))
        document.dispatchEvent(new KeyboardEvent('keydown', { key: '[', shiftKey: true }))
        document.dispatchEvent(new KeyboardEvent('keydown', { key: ']', shiftKey: true }))
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'n' }))

        expect(handlers.goToNextPage).toHaveBeenCalledTimes(1)
        expect(handlers.goToPrevPage).toHaveBeenCalledTimes(1)
        expect(handlers.goToFirstPage).toHaveBeenCalledTimes(1)
        expect(handlers.goToLastPage).toHaveBeenCalledTimes(1)
        expect(handlers.goToWrite).toHaveBeenCalledTimes(1)

        boardWrapper.unmount()
    })

    it('honors subscribe and ignore guards', async () => {
        const { handlers, wrapper: boardWrapper } = mountBoardShortcuts({
            canToggleSubscribe: () => false,
            shouldIgnoreShortcut: () => false,
        })
        await nextTick()

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'f' }))
        expect(handlers.toggleSubscribe).not.toHaveBeenCalled()

        boardWrapper.unmount()

        const ignored = mountBoardShortcuts({
            shouldIgnoreShortcut: () => true,
        })
        await nextTick()

        document.dispatchEvent(new KeyboardEvent('keydown', { key: ']' }))
        expect(ignored.handlers.goToNextPage).not.toHaveBeenCalled()

        ignored.wrapper.unmount()
    })
})
