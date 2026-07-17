import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { userApi } from '@/api/user'
import logger from '@/utils/logger'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'

type UserThemePreference = 'DARK' | 'LIGHT'

let themePersistQueue: Promise<void> = Promise.resolve()

type ThemePersistContext = {
    generation: number
    accessToken: string | null
    userId: number | null
    isCurrent: () => boolean
}

function enqueueThemePersist(theme: UserThemePreference, context: ThemePersistContext): Promise<void> {
    const controller = new AbortController()
    const stopSessionBoundary = subscribeAuthSessionBoundary(() => controller.abort())
    themePersistQueue = themePersistQueue
        .catch(() => undefined)
        .then(async () => {
            try {
                if (controller.signal.aborted || !context.isCurrent()) return
                await userApi.updateUserSettings({ theme }, { signal: controller.signal })
            } catch (error: unknown) {
                if (controller.signal.aborted || !context.isCurrent() || isCancellationError(error)) return
                logger.error('Failed to save theme setting:', error)
            } finally {
                stopSessionBoundary()
            }
        })

    return themePersistQueue
}

export function useThemePreference() {
    const authStore = useAuthStore()
    const themeStore = useThemeStore()

    async function persistTheme(theme: UserThemePreference) {
        if (!authStore.isAuthenticated) return

        const generation = authStore.sessionGeneration
        const accessToken = authStore.accessToken
        const userId = authStore.user?.userId ?? null
        await enqueueThemePersist(theme, {
            generation,
            accessToken,
            userId,
            isCurrent: () => authStore.isAuthenticated
                && authStore.sessionGeneration === generation
                && authStore.accessToken === accessToken
                && (authStore.user?.userId ?? null) === userId,
        })
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
