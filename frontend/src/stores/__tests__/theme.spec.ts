import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '../theme'

describe('Theme Store', () => {
    let store: ReturnType<typeof useThemeStore>

    beforeEach(() => {
        // Reset localStorage
        localStorage.clear()
        vi.clearAllMocks()

        setActivePinia(createPinia())
        store = useThemeStore()
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
        })

        it('initializes with light theme if localStorage has light', () => {
            localStorage.setItem('theme', 'light')
            setActivePinia(createPinia())
            store = useThemeStore()

            expect(store.isDark).toBe(false)
        })
    })

    describe('setTheme', () => {
        it('sets theme to dark when passed DARK', () => {
            store.setTheme('DARK')
            expect(store.isDark).toBe(true)
        })

        it('sets theme to light when passed LIGHT', () => {
            store.isDark = true
            store.setTheme('LIGHT')
            expect(store.isDark).toBe(false)
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
    })
})
