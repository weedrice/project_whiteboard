import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useThemePreference } from '../useThemePreference'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
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

    it('serializes rapid authenticated theme persistence in toggle order', async () => {
        const authStore = useAuthStore()
        authStore.accessToken = 'token'
        type ThemePersistResponse = Awaited<ReturnType<typeof userApi.updateUserSettings>>
        const firstPersist = createDeferred<ThemePersistResponse>()
        const secondPersist = createDeferred<ThemePersistResponse>()
        vi.mocked(userApi.updateUserSettings)
            .mockReturnValueOnce(firstPersist.promise)
            .mockReturnValueOnce(secondPersist.promise)

        const { toggleTheme } = useThemePreference()

        const firstToggle = toggleTheme()
        const secondToggle = toggleTheme()

        await vi.waitFor(() => {
            expect(userApi.updateUserSettings).toHaveBeenCalledTimes(1)
        })
        expect(userApi.updateUserSettings).toHaveBeenNthCalledWith(1, { theme: 'DARK' })

        firstPersist.resolve(apiSuccessResponse<typeof userApi.updateUserSettings>())
        await vi.waitFor(() => {
            expect(userApi.updateUserSettings).toHaveBeenCalledTimes(2)
        })
        expect(userApi.updateUserSettings).toHaveBeenNthCalledWith(2, { theme: 'LIGHT' })

        secondPersist.resolve(apiSuccessResponse<typeof userApi.updateUserSettings>())
        await expect(Promise.all([firstToggle, secondToggle])).resolves.toEqual([undefined, undefined])
    })
})
