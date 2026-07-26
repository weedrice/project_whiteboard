import { watch } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { unwrapApiData } from '@/api/response'
import { userSettingsSessionQueryKey } from '@/features/user/useUser'
import type { UserSettings } from '@/types/user'
import logger from '@/utils/logger'
import { Storage } from '@/utils/storage'
import { setAppLocale } from '@/i18n'
import { rememberUserTimeZone } from '@/utils/displayTimeZone'

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

    const applySettings = async (
        settings: Partial<UserSettings>,
        expectedSessionGeneration = authStore.sessionGeneration,
        expectedLoadGeneration = settingsLoadGeneration,
    ) => {
        // 설정 화면을 열지 않아도 저장된 표시 시간대가 앱 전체에 적용되게 한다.
        // 저장에 실패하더라도 테마·언어 적용까지 막지는 않는다.
        try {
            rememberUserTimeZone(settings.timezone)
        } catch (error) {
            logger.warn('Failed to apply display time zone', error)
        }
        if (settings.theme) {
            themeStore.setTheme(settings.theme)
        }
        if (settings.language) {
            const applied = await setAppLocale(
                settings.language.toLowerCase() as UserSettings['language'],
                () => (
                    authStore.isAuthenticated
                    && authStore.sessionGeneration === expectedSessionGeneration
                    && settingsLoadGeneration === expectedLoadGeneration
                ),
            )
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
                queryFn: async ({ signal }) => {
                    const { data } = await userApi.getUserSettings({ signal })
                    return data.success ? unwrapApiData(data) : null
                },
            })
            if (
                loadGeneration === settingsLoadGeneration
                && sessionGeneration === authStore.sessionGeneration
                && authStore.isAuthenticated
                && settings
            ) {
                await applySettings(settings, sessionGeneration, loadGeneration)
            }
        } catch (error) {
            logger.warn('Failed to load user settings:', error)
        }
    }

    watch(
        () => [authStore.isAuthenticated, authStore.sessionGeneration] as const,
        ([isAuthenticated, sessionGeneration], previous) => {
            settingsLoadGeneration += 1
            void setAppLocale('ko', () => (
                authStore.sessionGeneration === sessionGeneration
            ))
            if (isAuthenticated) {
                loadSettings()
            } else {
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
