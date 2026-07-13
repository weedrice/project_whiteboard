import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { unwrapApiData } from '@/api/response'
import logger from '@/utils/logger'
import {
    AUTH_SESSION_EVENT_KEY,
    ACCESS_TOKEN_KEY,
    clearStoredAuthTokens,
    getStoredAccessToken,
    persistAccessToken,
} from '@/utils/authTokenStorage'
import type { User, LoginCredentials, LoginUser } from '@/types'
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

function createLoginUserFallback(userData: LoginUser): User {
    return {
        userId: userData.userId,
        loginId: userData.loginId,
        displayName: userData.displayName,
        email: '',
        role: userData.role,
        status: 'ACTIVE',
        profileImageUrl: userData.profileImageUrl,
        theme: userData.theme,
        isEmailVerified: userData.isEmailVerified ?? userData.emailVerified ?? false,
        createdAt: '',
        points: userData.points,
    }
}

export function configureAuthSessionEffects(effects: Partial<AuthSessionEffects>) {
    authSessionEffects = {
        ...noopSessionEffects,
        ...effects,
    }
}

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null)
    const accessToken = ref<string | null>(null)
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
                const { accessToken: token, user: userData } = unwrapApiData(data)

                applyAuthenticatedSession(token, createLoginUserFallback(userData))

                const hydrated = await fetchUser({ skipAuthRefresh: true })
                return hydrated || Boolean(accessToken.value && user.value)
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
        }
    }

    async function fetchUser(config?: AxiosRequestConfig): Promise<boolean> {
        const token = accessToken.value ?? getStoredAccessToken()
        if (!token) {
            clearSessionState()
            return false
        }

        if (!accessToken.value) accessToken.value = token

        try {
            const { data } = await authApi.getMe(config)
            if (accessToken.value !== token) {
                return Boolean(accessToken.value && user.value)
            }
            if (data.success) {
                user.value = unwrapApiData(data)

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
            return false
        }
    }

    async function syncFromStoredAccessToken(token: string | null): Promise<boolean> {
        if (!token) {
            accessToken.value = null
            user.value = null
            return false
        }

        if (accessToken.value === token && user.value) {
            return true
        }

        accessToken.value = token
        return fetchUser({ skipAuthRefresh: true })
    }

    async function bootstrapSession(): Promise<boolean> {
        if (accessToken.value && user.value) {
            return true
        }

        try {
            const { data } = await authApi.refreshToken({
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            })
            if (!data.success) {
                clearSessionState()
                return false
            }
            const { accessToken: token } = unwrapApiData(data)
            setTokens(token)
            return fetchUser({ skipAuthRefresh: true })
        } catch (error: unknown) {
            logger.error('Bootstrap session failed:', error)
            clearSessionState()
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
        bootstrapSession,
        syncFromStoredAccessToken,
        setTokens,
        clearSessionState
    }
})

export function registerAuthStorageSync(authStore = useAuthStore()) {
    if (typeof window === 'undefined') {
        return () => undefined
    }

    const handleStorage = (event: StorageEvent) => {
        if (event.key !== AUTH_SESSION_EVENT_KEY && event.key !== ACCESS_TOKEN_KEY && event.key !== null) {
            return
        }

        void authStore.syncFromStoredAccessToken(null)
    }

    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
}
