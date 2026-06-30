import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useThemePreference } from '../useThemePreference'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import logger from '@/utils/logger'

vi.mock('@/api/user', () => ({
    userApi: {
        updateUserSettings: vi.fn()
    }
}))

vi.mock('@/api/auth', () => ({
    authApi: {
        logout: vi.fn(),
        login: vi.fn(),
        getMe: vi.fn()
    }
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn()
    }
}))

describe('useThemePreference', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        setActivePinia(createPinia())
        Object.defineProperty(window, 'matchMedia', {
            writable: true,
            value: vi.fn().mockImplementation(() => ({
                matches: false,
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
            })),
        })
    })

    it('toggles local theme and persists it for authenticated users', async () => {
        const authStore = useAuthStore()
        authStore.accessToken = 'token'
        vi.mocked(userApi.updateUserSettings).mockResolvedValue(apiSuccessResponse<typeof userApi.updateUserSettings>())

        const themeStore = useThemeStore()
        const { toggleTheme } = useThemePreference()

        await toggleTheme()

        expect(themeStore.isDark).toBe(true)
        expect(userApi.updateUserSettings).toHaveBeenCalledWith({ theme: 'DARK' })
    })

    it('toggles local theme without server persistence for guests', async () => {
        const themeStore = useThemeStore()
        const { toggleTheme } = useThemePreference()

        await toggleTheme()

        expect(themeStore.isDark).toBe(true)
        expect(userApi.updateUserSettings).not.toHaveBeenCalled()
    })

    it('keeps local theme when persistence fails', async () => {
        const authStore = useAuthStore()
        authStore.accessToken = 'token'
        vi.mocked(userApi.updateUserSettings).mockRejectedValue(new Error('Network error'))

        const themeStore = useThemeStore()
        const { toggleTheme } = useThemePreference()

        await expect(toggleTheme()).resolves.not.toThrow()

        expect(themeStore.isDark).toBe(true)
        expect(logger.error).toHaveBeenCalledWith('Failed to save theme setting:', expect.any(Error))
    })
})
