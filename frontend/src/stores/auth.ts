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
import { clearLoginRedirect } from '@/utils/authRedirect'
import { cancelPendingAuthRefresh } from '@/api/authRefreshSession'
import {
    cancelAuthRefreshCoordinator,
    coordinateAuthRefresh,
    rotateSharedAuthSessionId,
} from '@/api/authRefreshCoordinator'

interface AuthSessionEffects {
    syncThemeFromUser: (userData: User | null) => void
    handleSanctionedSession: () => void
    onSessionBoundary: (generation: number) => void
}

const noopSessionEffects: AuthSessionEffects = {
    syncThemeFromUser: () => undefined,
    handleSanctionedSession: () => undefined,
    onSessionBoundary: () => undefined,
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
    let bootstrapAttempted = false
    let bootstrapTerminalFailure = false
    let bootstrapRetryAt = 0
    let bootstrapInFlight: Promise<boolean> | null = null
    const sessionGeneration = ref(0)

    function resetBootstrapState() {
        bootstrapAttempted = false
        bootstrapTerminalFailure = false
        bootstrapRetryAt = 0
        bootstrapInFlight = null
    }

    function advanceSessionGeneration() {
        sessionGeneration.value += 1
        rotateSharedAuthSessionId()
        cancelPendingAuthRefresh()
        cancelAuthRefreshCoordinator()
        authSessionEffects.onSessionBoundary(sessionGeneration.value)
        return sessionGeneration.value
    }

    function syncThemeFromUser(userData: User | null) {
        authSessionEffects.syncThemeFromUser(userData)
    }

    function applyAuthenticatedSession(token: string, userData: User) {
        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        user.value = userData
        persistAccessToken(token)
        syncThemeFromUser(userData)
    }

    function clearSessionValues(broadcast = true) {
        accessToken.value = null
        user.value = null
        clearStoredAuthTokens(broadcast)
    }

    function clearSessionState(broadcast = true) {
        advanceSessionGeneration()
        resetBootstrapState()
        clearSessionValues(broadcast)
    }

    async function handleSanctionedSession() {
        authSessionEffects.handleSanctionedSession()
        await logout()
    }

    async function login(credentials: LoginCredentials): Promise<boolean> {
        const generation = sessionGeneration.value
        try {
            const { data } = await authApi.login(credentials)
            if (data.success) {
                const { accessToken: token, user: userData } = unwrapApiData(data)

                if (generation !== sessionGeneration.value) return false
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
        clearSessionState()
        clearLoginRedirect()
        try {
            await authApi.logout()
        } catch (error: unknown) {
            logger.error('Logout failed:', error)
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
            clearSessionState(false)
            return false
        }

        if (accessToken.value === token && user.value) {
            return true
        }

        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        return fetchUser({ skipAuthRefresh: true })
    }

    async function bootstrapSession(): Promise<boolean> {
        if (accessToken.value && user.value) {
            return true
        }
        if (bootstrapInFlight) {
            return bootstrapInFlight
        }
        if (bootstrapTerminalFailure || (bootstrapAttempted && Date.now() < bootstrapRetryAt)) {
            return false
        }

        bootstrapAttempted = true
        const generation = sessionGeneration.value
        const request = (async () => {
            try {
                const token = await coordinateAuthRefresh(async (signal) => {
                    if (generation !== sessionGeneration.value || accessToken.value !== null) {
                        throw new DOMException('Authentication session changed', 'AbortError')
                    }
                    const { data } = await authApi.refreshToken({
                        skipAuthRefresh: true,
                        skipGlobalErrorHandler: true,
                        signal,
                    })
                    if (!data.success) throw new Error('Bootstrap refresh failed')
                    return unwrapApiData(data).accessToken
                }, { previousToken: null })
                if (generation !== sessionGeneration.value) {
                    return Boolean(accessToken.value && user.value)
                }
                if (!applyNewSessionIfCurrent(generation, null, token)) return false
                return fetchUser({ skipAuthRefresh: true })
            } catch (error: unknown) {
                logger.error('Bootstrap session failed:', error)
                if (generation === sessionGeneration.value) {
                    clearSessionValues()
                    const status = error && typeof error === 'object'
                        ? (error as { response?: { status?: number } }).response?.status
                        : undefined
                    bootstrapTerminalFailure = status === 401 || status === 403
                    bootstrapRetryAt = bootstrapTerminalFailure
                        ? Number.POSITIVE_INFINITY
                        : Date.now() + 3000
                }
                return false
            }
        })()
        bootstrapInFlight = request

        try {
            return await request
        } finally {
            if (bootstrapInFlight === request) {
                bootstrapInFlight = null
            }
        }
    }

    function setTokens(token: string) {
        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        persistAccessToken(token)
    }

    function applyTokenIfCurrent(generation: number, previousToken: string | null, token: string) {
        if (sessionGeneration.value !== generation || accessToken.value !== previousToken || !token) {
            return false
        }
        resetBootstrapState()
        accessToken.value = token
        persistAccessToken(token)
        return true
    }

    function applyNewSessionIfCurrent(generation: number, previousToken: string | null, token: string) {
        if (sessionGeneration.value !== generation || accessToken.value !== previousToken || !token) {
            return false
        }
        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        persistAccessToken(token)
        return true
    }

    return {
        user,
        accessToken,
        sessionGeneration,
        isAuthenticated,
        isAdmin: computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN'),
        login,
        logout,
        handleSanctionedSession,
        fetchUser,
        bootstrapSession,
        syncFromStoredAccessToken,
        setTokens,
        applyTokenIfCurrent,
        applyNewSessionIfCurrent,
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
