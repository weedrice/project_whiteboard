import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '../theme'
import { useAuthStore } from '../auth'
import { userApi } from '@/api/user'

// Mock dependencies
vi.mock('@/api/user', () => ({
    userApi: {
        updateUserSettings: vi.fn()
    }
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn()
    }
}))

// We need to properly mock auth store
vi.mock('../auth', () => ({
    useAuthStore: vi.fn()
}))

describe('Theme Store', () => {
    let store: ReturnType<typeof useThemeStore>

    beforeEach(() => {
        // Reset localStorage
        localStorage.clear()
        vi.clearAllMocks()

        // Default mock: not authenticated
        vi.mocked(useAuthStore).mockReturnValue({
            isAuthenticated: false
        } as any)

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

        it('saves theme to server when authenticated', async () => {
            vi.mocked(useAuthStore).mockReturnValue({
                isAuthenticated: true
            } as any)

            setActivePinia(createPinia())
            store = useThemeStore()

            vi.mocked(userApi.updateUserSettings).mockResolvedValue({} as any)

            await store.toggleTheme()

            expect(userApi.updateUserSettings).toHaveBeenCalledWith({
                theme: 'DARK'
            })
        })

        it('does not save theme to server when not authenticated', async () => {
            vi.mocked(useAuthStore).mockReturnValue({
                isAuthenticated: false
            } as any)

            setActivePinia(createPinia())
            store = useThemeStore()

            await store.toggleTheme()

            expect(userApi.updateUserSettings).not.toHaveBeenCalled()
        })

        it('handles API error gracefully', async () => {
            vi.mocked(useAuthStore).mockReturnValue({
                isAuthenticated: true
            } as any)

            setActivePinia(createPinia())
            store = useThemeStore()

            vi.mocked(userApi.updateUserSettings).mockRejectedValue(new Error('Network error'))

            // Should not throw
            await expect(store.toggleTheme()).resolves.not.toThrow()
            expect(store.isDark).toBe(true) // Theme still toggled locally
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
