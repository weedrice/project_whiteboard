import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import logger from '@/utils/logger'

export function useThemePreference() {
    const authStore = useAuthStore()
    const themeStore = useThemeStore()

    async function persistCurrentTheme() {
        if (!authStore.isAuthenticated) return

        try {
            await userApi.updateUserSettings({
                theme: themeStore.isDark ? 'DARK' : 'LIGHT'
            })
        } catch (error: unknown) {
            logger.error('Failed to save theme setting:', error)
        }
    }

    async function toggleTheme() {
        await themeStore.toggleTheme()
        await persistCurrentTheme()
    }

    return {
        toggleTheme,
        persistCurrentTheme
    }
}
