import { createAuthScopedQueryOptions } from '@/utils/authScopedQuery'
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
import {
    captureAuthSessionIntent,
    isAuthSessionIntentCurrent,
    type AuthSessionIntentSource,
} from '@/utils/authSessionIntent'

type AppAuthStore = AuthSessionIntentSource & {
    isAuthenticated: boolean
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
        expectedSession = captureAuthSessionIntent(authStore),
        expectedLoadGeneration = settingsLoadGeneration,
    ) => {
        const canApply = () => (
            authStore.isAuthenticated
            && expectedSession.userId !== null
            && isAuthSessionIntentCurrent(authStore, expectedSession)
            && settingsLoadGeneration === expectedLoadGeneration
        )
        if (!canApply()) return
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
                canApply,
            )
            if (!applied) {
                logger.warn('Failed to load locale messages during settings sync')
            }
        }
    }

    const loadSettings = async () => {
        const loadGeneration = ++settingsLoadGeneration
        const expectedSession = captureAuthSessionIntent(authStore)
        // Bootstrap publishes the token before /me resolves. Wait until the
        // principal is known so this fixed query intent survives hydration.
        if (!authStore.isAuthenticated || expectedSession.userId === null) return

        try {
            const settings = await queryClient.fetchQuery({
                ...createAuthScopedQueryOptions(authStore, userSettingsSessionQueryKey(expectedSession.sessionGeneration), async ({ requestConfig }) => {
                    const { data } = await userApi.getUserSettings(requestConfig)
                    return data.success ? unwrapApiData(data) : null
                }, expectedSession),
                meta: { authScoped: true },
            })
            if (
                loadGeneration === settingsLoadGeneration
                && isAuthSessionIntentCurrent(authStore, expectedSession)
                && authStore.isAuthenticated
                && settings
            ) {
                await applySettings(settings, expectedSession, loadGeneration)
            }
        } catch (error) {
            logger.warn('Failed to load user settings:', error)
        }
    }

    watch(
        [
            () => authStore.isAuthenticated,
            () => authStore.sessionGeneration,
            () => authStore.user?.userId ?? null,
        ],
        ([isAuthenticated, sessionGeneration, userId], previous) => {
            settingsLoadGeneration += 1
            const expectedSession = { sessionGeneration, userId }
            void setAppLocale('ko', () => (
                authStore.isAuthenticated === isAuthenticated
                && isAuthSessionIntentCurrent(authStore, expectedSession)
            ))
            if (previous?.[2] != null && previous[2] !== userId) {
                // The key uses generation, so also discard cached/in-flight
                // settings when a known identity changes within that generation.
                queryClient.removeQueries({ queryKey: userSettingsSessionQueryKey(previous[1]) })
            }
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
