import { watch } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { unwrapApiData } from '@/api/response'
import { userSettingsSessionQueryKey } from '@/composables/useUser'
import type { UserSettings } from '@/types/user'
import logger from '@/utils/logger'
import { Storage } from '@/utils/storage'
import { setAppLocale } from '@/i18n'

type AppAuthStore = {
    isAuthenticated: boolean
    sessionGeneration: number
}

type AppThemeStore = {
    setTheme: (theme: 'LIGHT' | 'DARK') => void
}

export function useAppUserSettingsSync(
    authStore: AppAuthStore,
    themeStore: AppThemeStore,
    queryClient: QueryClient,
) {
    let settingsLoadGeneration = 0

    const applySettings = async (settings: Partial<UserSettings>) => {
        if (settings.theme) {
            themeStore.setTheme(settings.theme)
        }
        if (settings.language) {
            const applied = await setAppLocale(settings.language.toLowerCase() as UserSettings['language'])
            if (!applied) {
                logger.warn('Failed to load locale messages during settings sync')
            }
        }
    }

    const loadSettings = async () => {
        const loadGeneration = ++settingsLoadGeneration
        const sessionGeneration = authStore.sessionGeneration
        if (!authStore.isAuthenticated) return

        try {
            const settings = await queryClient.fetchQuery({
                queryKey: userSettingsSessionQueryKey(sessionGeneration),
                meta: { authScoped: true },
                queryFn: async () => {
                    const { data } = await userApi.getUserSettings()
                    return data.success ? unwrapApiData(data) : null
                },
            })
            if (
                loadGeneration === settingsLoadGeneration
                && sessionGeneration === authStore.sessionGeneration
                && authStore.isAuthenticated
                && settings
            ) {
                await applySettings(settings)
            }
        } catch (error) {
            logger.warn('Failed to load user settings:', error)
        }
    }

    watch(
        () => [authStore.isAuthenticated, authStore.sessionGeneration] as const,
        ([isAuthenticated], previous) => {
            if (isAuthenticated) {
                loadSettings()
            } else {
                settingsLoadGeneration += 1
                queryClient.removeQueries({
                    queryKey: userSettingsSessionQueryKey(previous?.[1] ?? authStore.sessionGeneration),
                })
                const storedTheme = Storage.getString('theme')
                if (storedTheme) {
                    themeStore.setTheme(storedTheme === 'dark' ? 'DARK' : 'LIGHT')
                }
            }
        },
    )

    return {
        applySettings,
        loadSettings,
    }
}
