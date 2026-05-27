import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import logger from '@/utils/logger'
import { clearStoredAuthTokens, getStoredAccessToken, persistAccessToken } from '@/utils/authTokenStorage'
import type { User, LoginCredentials } from '@/types'
import type { AxiosRequestConfig } from 'axios'

interface AuthSessionEffects {
    syncThemeFromUser: (userData: User | null) => void
    handleSanctionedSession: () => void
}

const noopSessionEffects: AuthSessionEffects = {
    syncThemeFromUser: () => undefined,
    handleSanctionedSession: () => undefined,
}

let authSessionEffects: AuthSessionEffects = noopSessionEffects

export function configureAuthSessionEffects(effects: Partial<AuthSessionEffects>) {
    authSessionEffects = {
        ...noopSessionEffects,
        ...effects,
    }
}

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const accessToken = ref<string | null>(getStoredAccessToken())
    const isAuthenticated = computed(() => !!accessToken.value)

    function syncThemeFromUser(userData: User | null) {
        authSessionEffects.syncThemeFromUser(userData)
    }

    function applyAuthenticatedSession(token: string, userData: User) {
        accessToken.value = token
        user.value = userData
        persistAccessToken(token)
        syncThemeFromUser(userData)
    }

    function clearSessionState() {
        accessToken.value = null
        user.value = null
        clearStoredAuthTokens()
    }

    async function handleSanctionedSession() {
        authSessionEffects.handleSanctionedSession()
        await logout()
    }

    async function login(credentials: LoginCredentials): Promise<boolean> {
        try {
            const { data } = await authApi.login(credentials)
            if (data.success) {
                const { accessToken: token, user: userData } = data.data

                applyAuthenticatedSession(token, userData)

                return true
            }
            return false
        } catch (error: unknown) {
            logger.error('Login failed:', error)
            throw error
        }
    }

    async function logout() {
        try {
            await authApi.logout()
        } catch (error: unknown) {
            logger.error('Logout failed:', error)
        } finally {
            clearSessionState()
            // themeStore.setTheme('LIGHT') // Reset to default on logout -> Removed to persist theme
        }
    }

    async function fetchUser(config?: AxiosRequestConfig): Promise<boolean> {
        // Double check token existence
        const token = getStoredAccessToken()
        if (!token) {
            clearSessionState()
            return false
        }

        if (!accessToken.value) accessToken.value = token

        try {
            const { data } = await authApi.getMe(config)
            if (data.success) {
                user.value = data.data

                // Check for sanctions
                if (user.value?.status === 'SANCTIONED') {
                    await handleSanctionedSession()
                    return false
                }

                syncThemeFromUser(user.value)

                return true
            }
            return false
        } catch (error: unknown) {
            logger.error('Fetch user failed:', error)
            // 401 에러는 axios 인터셉터에서 refresh token으로 처리함
            // 여기서는 로그만 남기고, 인터셉터가 refresh 실패 시 로그아웃 처리
            // 네트워크 에러나 서버 에러(500 등)는 로그아웃하지 않음
            return false
        }
    }

    function setTokens(token: string) {
        accessToken.value = token
        persistAccessToken(token)
    }

    return {
        user,
        accessToken,
        isAuthenticated,
        isAdmin: computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN'),
        login,
        logout,
        handleSanctionedSession,
        fetchUser,
        setTokens,
        clearSessionState
    }
})
