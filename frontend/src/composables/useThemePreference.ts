import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import logger from '@/utils/logger'

type UserThemePreference = 'DARK' | 'LIGHT'

let themePersistQueue: Promise<void> = Promise.resolve()

function enqueueThemePersist(theme: UserThemePreference): Promise<void> {
    themePersistQueue = themePersistQueue
        .catch(() => undefined)
        .then(async () => {
            try {
                await userApi.updateUserSettings({ theme })
            } catch (error: unknown) {
                logger.error('Failed to save theme setting:', error)
            }
        })

    return themePersistQueue
}

export function useThemePreference() {
    const authStore = useAuthStore()
    const themeStore = useThemeStore()

    async function persistTheme(theme: UserThemePreference) {
        if (!authStore.isAuthenticated) return

        await enqueueThemePersist(theme)
    }

    async function persistCurrentTheme() {
        await persistTheme(themeStore.isDark ? 'DARK' : 'LIGHT')
    }

    async function toggleTheme() {
        const nextTheme = themeStore.isDark ? 'LIGHT' : 'DARK'
        themeStore.setTheme(nextTheme)
        await persistTheme(nextTheme)
    }

    return {
        toggleTheme,
        persistCurrentTheme
    }
}
