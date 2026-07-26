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
import {
    publishAuthBoundary,
    registerAuthBoundaryListener,
    type AuthBoundaryKind,
} from '@/api/authBoundaryChannel'
import { detachBrowserPushSubscriptionForSession } from '@/features/notifications/pushSubscriptions'

export type BootstrapSessionState = 'idle' | 'loading' | 'authenticated' | 'transient-error' | 'terminal-error'
export type UserHydrationResult = 'success' | 'transient-failure' | 'terminal-failure' | 'stale'

interface AuthSessionEffects {
    syncThemeFromUser: (userData: User | null) => void
    onSessionBoundary: (generation: number) => void
    /**
     * 세션의 **주체가 바뀌었을 때만** 부른다(로그인·계정 전환·로그아웃).
     *
     * `onSessionBoundary`는 같은 사용자가 토큰만 새로 받는 경우(부트스트랩, 갱신)에도
     * 불리므로, 계정에 딸린 기기 저장값을 비우는 용도로는 쓸 수 없다. 새로고침마다
     * 지워져 버린다.
     */
    onPrincipalChange: () => void
}

const noopSessionEffects: AuthSessionEffects = {
    syncThemeFromUser: () => undefined,
    onSessionBoundary: () => undefined,
    onPrincipalChange: () => undefined,
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
    const bootstrapState = ref<BootstrapSessionState>('idle')

    function resetBootstrapState() {
        bootstrapAttempted = false
        bootstrapTerminalFailure = false
        bootstrapRetryAt = 0
        bootstrapInFlight = null
        bootstrapState.value = 'idle'
    }

    function advanceSessionGeneration() {
        const previousAccessToken = accessToken.value
        sessionGeneration.value += 1
        rotateSharedAuthSessionId()
        cancelPendingAuthRefresh()
        cancelAuthRefreshCoordinator()
        authSessionEffects.onSessionBoundary(sessionGeneration.value)
        if (previousAccessToken) {
            void detachBrowserPushSubscriptionForSession(previousAccessToken)
        }
        return sessionGeneration.value
    }

    function syncThemeFromUser(userData: User | null) {
        authSessionEffects.syncThemeFromUser(userData)
    }

    function applyAuthenticatedSession(token: string, userData: User) {
        const boundary: AuthBoundaryKind = user.value && user.value.userId !== userData.userId
            ? 'account-switch'
            : 'login'
        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        user.value = userData
        persistAccessToken(token)
        syncThemeFromUser(userData)
        authSessionEffects.onPrincipalChange()
        publishAuthBoundary(boundary)
    }

    function clearSessionValues(broadcast = true) {
        accessToken.value = null
        user.value = null
        clearStoredAuthTokens(broadcast)
    }

    function clearSessionState(broadcast = true) {
        advanceSessionGeneration()
        resetBootstrapState()
        clearSessionValues(false)
        // 다른 탭이 알려 온 로그아웃(broadcast=false)도 이 탭에서는 주체 변경이다.
        authSessionEffects.onPrincipalChange()
        if (broadcast) publishAuthBoundary('logout')
    }

    async function login(credentials: LoginCredentials, config?: AxiosRequestConfig): Promise<boolean> {
        const generation = sessionGeneration.value
        try {
            const { data } = await authApi.login(credentials, config)
            if (data.success) {
                const { accessToken: token, user: userData } = unwrapApiData(data)

                if (config?.signal?.aborted || generation !== sessionGeneration.value) return false
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

    async function fetchUser(config?: AxiosRequestConfig, expectedUserId?: number | null): Promise<boolean> {
        const result = await hydrateUser(config, expectedUserId)
        if (result === 'stale') return Boolean(accessToken.value && user.value)
        return result === 'success'
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

        if (accessToken.value) {
            bootstrapAttempted = true
            bootstrapState.value = 'loading'
            const generation = sessionGeneration.value
            const request = (async () => {
                const hydration = await hydrateUser({
                    skipAuthRefresh: true,
                    skipGlobalErrorHandler: true,
                })
                if (hydration === 'stale' || (
                    hydration !== 'terminal-failure'
                    && generation !== sessionGeneration.value
                )) {
                    return Boolean(accessToken.value && user.value)
                }
                if (hydration === 'success') {
                    bootstrapState.value = 'authenticated'
                    return true
                }
                if (hydration === 'terminal-failure') {
                    bootstrapTerminalFailure = true
                    bootstrapRetryAt = Number.POSITIVE_INFINITY
                    bootstrapState.value = 'terminal-error'
                    return false
                }
                bootstrapRetryAt = Date.now() + 3000
                bootstrapState.value = 'transient-error'
                return false
            })()
            bootstrapInFlight = request
            try {
                return await request
            } finally {
                if (bootstrapInFlight === request) bootstrapInFlight = null
            }
        }

        bootstrapAttempted = true
        bootstrapState.value = 'loading'
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
                    if (!data.success) {
                        const failure = new Error('Bootstrap refresh failed') as Error & {
                            response: { status: number }
                        }
                        failure.response = { status: 401 }
                        throw failure
                    }
                    return unwrapApiData(data).accessToken
                }, { previousToken: null })
                if (generation !== sessionGeneration.value) {
                    return Boolean(accessToken.value && user.value)
                }
                if (!applyNewSessionIfCurrent(generation, null, token, false)) return false
                const hydrationGeneration = sessionGeneration.value
                const hydration = await hydrateUser({
                    skipAuthRefresh: true,
                    skipGlobalErrorHandler: true,
                })
                if (hydration === 'stale' || (
                    hydration !== 'terminal-failure'
                    && hydrationGeneration !== sessionGeneration.value
                )) {
                    return Boolean(accessToken.value && user.value)
                }
                if (hydration === 'success') {
                    bootstrapState.value = 'authenticated'
                    return true
                }
                if (hydration === 'terminal-failure') {
                    bootstrapTerminalFailure = true
                    bootstrapRetryAt = Number.POSITIVE_INFINITY
                    bootstrapState.value = 'terminal-error'
                    return false
                }
                bootstrapRetryAt = Date.now() + 3000
                bootstrapState.value = 'transient-error'
                return false
            } catch (error: unknown) {
                logger.error('Bootstrap session failed:', error)
                if (generation === sessionGeneration.value) {
                    const status = error && typeof error === 'object'
                        ? (error as { response?: { status?: number } }).response?.status
                        : undefined
                    bootstrapTerminalFailure = status === 401 || status === 403
                    if (bootstrapTerminalFailure) {
                        clearSessionValues()
                    }
                    bootstrapRetryAt = bootstrapTerminalFailure
                        ? Number.POSITIVE_INFINITY
                        : Date.now() + 3000
                    bootstrapState.value = bootstrapTerminalFailure ? 'terminal-error' : 'transient-error'
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

    async function retryBootstrapSession(): Promise<boolean> {
        if (bootstrapState.value !== 'transient-error') return false
        bootstrapAttempted = false
        bootstrapRetryAt = 0
        return bootstrapSession()
    }

    async function hydrateUser(
        config?: AxiosRequestConfig,
        expectedUserId?: number | null,
    ): Promise<UserHydrationResult> {
        const token = accessToken.value ?? getStoredAccessToken()
        if (!token) {
            clearSessionState()
            return 'terminal-failure'
        }

        if (!accessToken.value) accessToken.value = token

        try {
            const { data } = await authApi.getMe(config)
            if (accessToken.value !== token) return 'stale'
            if (!data.success) return 'transient-failure'

            const nextUser = unwrapApiData(data)
            if (expectedUserId != null && nextUser.userId !== expectedUserId) {
                clearSessionState()
                return 'terminal-failure'
            }
            user.value = nextUser

            syncThemeFromUser(user.value)
            return 'success'
        } catch (error: unknown) {
            logger.error('Fetch user failed:', error)
            const status = error && typeof error === 'object'
                ? (error as { response?: { status?: number } }).response?.status
                : undefined
            if (status === 401 || status === 403) {
                clearSessionState()
                return 'terminal-failure'
            }
            return 'transient-failure'
        }
    }

    function setTokens(token: string) {
        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        persistAccessToken(token)
        publishAuthBoundary(user.value ? 'account-switch' : 'login')
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

    function applyNewSessionIfCurrent(
        generation: number,
        previousToken: string | null,
        token: string,
        broadcastBoundary = true,
    ) {
        if (sessionGeneration.value !== generation || accessToken.value !== previousToken || !token) {
            return false
        }
        advanceSessionGeneration()
        resetBootstrapState()
        accessToken.value = token
        persistAccessToken(token)
        // 부트스트랩은 broadcastBoundary=false로 들어온다. 같은 사용자가 토큰만 새로
        // 받는 경우라 주체 변경이 아니다. 여기서 주체 변경으로 처리하면 새로고침마다
        // 계정 설정에서 온 기기 저장값이 지워진다.
        if (broadcastBoundary) {
            authSessionEffects.onPrincipalChange()
            publishAuthBoundary(user.value ? 'account-switch' : 'login')
        }
        return true
    }

    return {
        user,
        accessToken,
        sessionGeneration,
        bootstrapState,
        isAuthenticated,
        isAdmin: computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'SUPER_ADMIN'),
        login,
        logout,
        fetchUser,
        bootstrapSession,
        retryBootstrapSession,
        hydrateUser,
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

    const applyExternalBoundary = () => {
        void authStore.syncFromStoredAccessToken(null)
    }

    const stopBoundaryListener = registerAuthBoundaryListener(applyExternalBoundary)
    const handleStorage = (event: StorageEvent) => {
        if (event.key !== AUTH_SESSION_EVENT_KEY && event.key !== ACCESS_TOKEN_KEY && event.key !== null) {
            return
        }

        applyExternalBoundary()
    }

    window.addEventListener('storage', handleStorage)
    return () => {
        stopBoundaryListener()
        window.removeEventListener('storage', handleStorage)
    }
}
