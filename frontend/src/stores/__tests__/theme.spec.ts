import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '../theme'

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
        warn: vi.fn(),
    },
}))

describe('Theme Store', () => {
    let store: ReturnType<typeof useThemeStore>
    const localStoragePrototype = Object.getPrototypeOf(window.localStorage)

    beforeEach(() => {
        // Reset localStorage
        localStorage.clear()
        document.documentElement.classList.remove('dark')
        vi.clearAllMocks()

        setActivePinia(createPinia())
        store = useThemeStore()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    describe('initialization', () => {
        it('initializes with light theme by default', () => {
            expect(store.isDark).toBe(false)
        })

        it('initializes with dark theme if stored in localStorage', () => {
            localStorage.setItem('theme', 'dark')
            setActivePinia(createPinia())
            store = useThemeStore()

            expect(store.isDark).toBe(true)
            expect(document.documentElement.classList.contains('dark')).toBe(true)
        })

        it('initializes with light theme if localStorage has light', () => {
            localStorage.setItem('theme', 'light')
            setActivePinia(createPinia())
            store = useThemeStore()

            expect(store.isDark).toBe(false)
        })

        it('falls back to system preference when localStorage read is blocked', () => {
            vi.spyOn(localStoragePrototype, 'getItem').mockImplementation(() => {
                throw new Error('localStorage blocked')
            })
            setActivePinia(createPinia())

            expect(() => {
                store = useThemeStore()
            }).not.toThrow()
            expect(store.isDark).toBe(false)
            expect(document.documentElement.classList.contains('dark')).toBe(false)
        })

        it('applies system dark preference without persisting an explicit theme', () => {
            const originalMatchMedia = window.matchMedia
            Object.defineProperty(window, 'matchMedia', {
                configurable: true,
                value: vi.fn().mockReturnValue({ matches: true }),
            })
            setActivePinia(createPinia())

            store = useThemeStore()

            expect(store.isDark).toBe(true)
            expect(document.documentElement.classList.contains('dark')).toBe(true)
            expect(localStorage.getItem('theme')).toBeNull()

            Object.defineProperty(window, 'matchMedia', {
                configurable: true,
                value: originalMatchMedia,
            })
        })

        it('initializes safely when matchMedia is unavailable', () => {
            const originalMatchMedia = window.matchMedia
            Object.defineProperty(window, 'matchMedia', {
                configurable: true,
                value: undefined,
            })
            setActivePinia(createPinia())

            expect(() => {
                store = useThemeStore()
            }).not.toThrow()
            expect(store.isDark).toBe(false)

            Object.defineProperty(window, 'matchMedia', {
                configurable: true,
                value: originalMatchMedia,
            })
        })
    })

    describe('setTheme', () => {
        it('sets theme to dark when passed DARK', () => {
            store.setTheme('DARK')
            expect(store.isDark).toBe(true)
            expect(document.documentElement.classList.contains('dark')).toBe(true)
        })

        it('sets theme to light when passed LIGHT', () => {
            store.isDark = true
            store.setTheme('LIGHT')
            expect(store.isDark).toBe(false)
            expect(document.documentElement.classList.contains('dark')).toBe(false)
        })
    })

    describe('toggleTheme', () => {
        it('toggles from light to dark', async () => {
            expect(store.isDark).toBe(false)

            await store.toggleTheme()

            expect(store.isDark).toBe(true)
        })

        it('toggles from dark to light', async () => {
            store.isDark = true

            await store.toggleTheme()

            expect(store.isDark).toBe(false)
        })

        it('does not throw when toggling local theme', async () => {
            await expect(store.toggleTheme()).resolves.not.toThrow()
            expect(store.isDark).toBe(true)
        })
    })

    describe('localStorage sync', () => {
        it('updates localStorage when theme changes to dark', async () => {
            store.setTheme('DARK')
            await new Promise(resolve => setTimeout(resolve, 0))
            expect(localStorage.getItem('theme')).toBe('dark')
        })

        it('updates localStorage when theme changes to light', async () => {
            store.setTheme('DARK')
            await new Promise(resolve => setTimeout(resolve, 0))
            store.setTheme('LIGHT')
            await new Promise(resolve => setTimeout(resolve, 0))
            expect(localStorage.getItem('theme')).toBe('light')
        })

        it('does not throw when localStorage write is blocked', async () => {
            vi.spyOn(localStoragePrototype, 'setItem').mockImplementation(() => {
                throw new Error('localStorage blocked')
            })

            expect(() => store.setTheme('DARK')).not.toThrow()
            await new Promise(resolve => setTimeout(resolve, 0))

            expect(store.isDark).toBe(true)
        })
    })
})
