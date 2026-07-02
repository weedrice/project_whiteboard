import { watch, type Ref } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { unwrapApiData } from '@/api/response'
import { userSettingsQueryKey } from '@/composables/useUser'
import type { UserSettings } from '@/types/user'
import logger from '@/utils/logger'
import { Storage } from '@/utils/storage'

type AppAuthStore = {
    isAuthenticated: boolean
}

type AppThemeStore = {
    setTheme: (theme: 'LIGHT' | 'DARK') => void
}

export function useAppUserSettingsSync(
    authStore: AppAuthStore,
    themeStore: AppThemeStore,
    queryClient: QueryClient,
    locale: Ref<string>,
) {
    const applySettings = (settings: Partial<UserSettings>) => {
        if (settings.theme) {
            themeStore.setTheme(settings.theme)
        }
        if (settings.language) {
            locale.value = settings.language.toLowerCase()
        }
    }

    const loadSettings = async () => {
        if (!authStore.isAuthenticated) return

        try {
            const settings = await queryClient.fetchQuery({
                queryKey: userSettingsQueryKey,
                queryFn: async () => {
                    const { data } = await userApi.getUserSettings()
                    return data.success ? unwrapApiData(data) : null
                },
            })
            if (authStore.isAuthenticated && settings) {
                applySettings(settings)
            }
        } catch (error) {
            logger.warn('Failed to load user settings:', error)
        }
    }

    watch(() => authStore.isAuthenticated, (newVal) => {
        if (newVal) {
            loadSettings()
        } else {
            queryClient.removeQueries({ queryKey: userSettingsQueryKey })
            const storedTheme = Storage.getString('theme')
            if (storedTheme) {
                themeStore.setTheme(storedTheme === 'dark' ? 'DARK' : 'LIGHT')
            }
        }
    })

    return {
        applySettings,
        loadSettings,
    }
}
