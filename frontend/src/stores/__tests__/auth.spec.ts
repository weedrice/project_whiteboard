import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { configureAuthSessionEffects, registerAuthStorageSync, useAuthStore } from '../auth'
import { authApi } from '@/api/auth'
import logger from '@/utils/logger'
import { createDeferred } from '@/test/async'
import {
    ACCESS_TOKEN_KEY,
    AUTH_SESSION_CLEARED_EVENT_PREFIX,
    AUTH_SESSION_EVENT_KEY,
    clearStoredAuthTokens,
    getStoredAccessToken,
    persistAccessToken,
} from '@/utils/authTokenStorage'
import {
    authLoginFailureResponse,
    authLoginResponse,
    authLogoutResponse,
    authUser,
    authUserFailureResponse,
    authUserResponse,
} from './storeTestFixtures'
import { detachBrowserPushSubscriptionForSession } from '@/features/notifications/pushSubscriptions'

// Mock dependencies
vi.mock('@/api/auth', () => ({
    authApi: {
        login: vi.fn(),
        logout: vi.fn(),
        refreshToken: vi.fn(),
        getMe: vi.fn()
    }
}))

vi.mock('@/features/notifications/pushSubscriptions', () => ({
    detachBrowserPushSubscriptionForSession: vi.fn().mockResolvedValue(undefined),
}))

const mockSyncThemeFromUser = vi.fn()
const mockSessionBoundary = vi.fn()
const mockPrincipalChange = vi.fn()
const mockExplicitLogout = vi.fn()

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn()
    }
}))

vi.mock('@/utils/storage', () => ({
    Storage: {
        getString: vi.fn((key: string) => localStorage.getItem(key)),
        setString: vi.fn((key: string, value: string) => localStorage.setItem(key, value)),
        remove: vi.fn((key: string) => localStorage.removeItem(key))
    },
    SessionStorage: {
        getString: vi.fn((key: string) => sessionStorage.getItem(key)),
        setString: vi.fn((key: string, value: string) => sessionStorage.setItem(key, value)),
        remove: vi.fn((key: string) => sessionStorage.removeItem(key)),
    },
}))

describe('Auth Store', () => {
    let store: ReturnType<typeof useAuthStore>

    beforeEach(() => {
        vi.useRealTimers()
        setActivePinia(createPinia())
        localStorage.clear()
        sessionStorage.clear()
        clearStoredAuthTokens()
        vi.clearAllMocks()
        configureAuthSessionEffects({
            syncThemeFromUser: mockSyncThemeFromUser,
            onSessionBoundary: mockSessionBoundary,
            onPrincipalChange: mockPrincipalChange,
            onExplicitLogout: mockExplicitLogout,
        })
        store = useAuthStore()
    })

    it('initializes with no user', () => {
        expect(store.user).toBeNull()
        expect(store.isAuthenticated).toBe(false)
    })

    it('does not initialize access token from legacy localStorage', () => {
        // Reset Pinia to ensure fresh store initialization
        setActivePinia(createPinia())
        localStorage.setItem('accessToken', 'test-token')
        store = useAuthStore()
        expect(store.accessToken).toBeNull()
        expect(store.isAuthenticated).toBe(false)
    })

    describe('login', () => {
        it('handles successful login', async () => {
            const loginUser = authUser({ theme: 'DARK' })
            const hydratedUser = authUser({ theme: 'DARK', isEmailVerified: true })
            vi.mocked(authApi.login).mockResolvedValue(authLoginResponse(loginUser))
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(hydratedUser))

            const result = await store.login({ loginId: 'test', password: 'password' })

            expect(result).toBe(true)
            expect(store.accessToken).toBe('new-token')
            expect(store.user).toEqual(hydratedUser)
            expect(getStoredAccessToken()).toBe('new-token')
            expect(localStorage.getItem('refreshToken')).toBeNull()

            expect(authApi.getMe).toHaveBeenCalledWith({ skipAuthRefresh: true })
            expect(mockSyncThemeFromUser).toHaveBeenLastCalledWith(hydratedUser)
        })

        it('keeps the normalized login user when profile hydration temporarily fails', async () => {
            vi.mocked(authApi.login).mockResolvedValue(authLoginResponse({
                userId: 1,
                loginId: 'test',
                displayName: 'Test User',
                emailVerified: true,
            }))
            vi.mocked(authApi.getMe).mockRejectedValue(new Error('temporary failure'))

            const result = await store.login({ loginId: 'test', password: 'password' })

            expect(result).toBe(true)
            expect(store.user?.isEmailVerified).toBe(true)
            expect(store.accessToken).toBe('new-token')
        })

        it('handles login failure', async () => {
            const error = new Error('Login failed')
            vi.mocked(authApi.login).mockRejectedValue(error)

            await expect(store.login({ loginId: 'test', password: 'wrong' })).rejects.toThrow('Login failed')
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
        })

        it('returns false and keeps state when success flag is false', async () => {
            vi.mocked(authApi.login).mockResolvedValue(authLoginFailureResponse(authUser({ loginId: 'ignored' })))

            const result = await store.login({ loginId: 'test', password: 'password' })

            expect(result).toBe(false)
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
        })

        it('does not apply a successful response after its login request is aborted', async () => {
            const pendingLogin = createDeferred<Awaited<ReturnType<typeof authApi.login>>>()
            const controller = new AbortController()
            vi.mocked(authApi.login).mockReturnValueOnce(pendingLogin.promise)

            const login = store.login(
                { loginId: 'stale', password: 'password' },
                { signal: controller.signal },
            )
            controller.abort()
            pendingLogin.resolve(authLoginResponse(authUser({ loginId: 'stale' }), 'stale-token'))

            await expect(login).resolves.toBe(false)
            expect(authApi.login).toHaveBeenCalledWith(
                { loginId: 'stale', password: 'password' },
                { signal: controller.signal },
            )
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
            expect(authApi.getMe).not.toHaveBeenCalled()
        })
    })

    it('ignores a user response that belongs to a replaced access token', async () => {
        const pendingProfile = createDeferred<Awaited<ReturnType<typeof authApi.getMe>>>()
        const currentUser = authUser({ userId: 2, loginId: 'current' })
        store.accessToken = 'old-token'
        vi.mocked(authApi.getMe).mockReturnValue(pendingProfile.promise)

        const pendingFetch = store.fetchUser()
        store.accessToken = 'new-token'
        store.user = currentUser
        pendingProfile.resolve(authUserResponse(authUser({ userId: 1, loginId: 'stale' })))

        expect(await pendingFetch).toBe(true)
        expect(store.user).toEqual(currentUser)
    })

    describe('logout', () => {
        beforeEach(() => {
            store.accessToken = 'token'
            store.user = authUser()
            persistAccessToken('token')
        })

        it('handles successful logout', async () => {
            sessionStorage.setItem('loginRedirect', 'stale')
            vi.mocked(authApi.logout).mockResolvedValue(authLogoutResponse())

            await store.logout()

            expect(authApi.logout).toHaveBeenCalled()
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
            expect(localStorage.getItem('refreshToken')).toBeNull()
            expect(sessionStorage.getItem('loginRedirect')).toBeNull()
            expect(mockExplicitLogout).toHaveBeenCalledWith(1)
        })

        it('cleans up state even if api call fails', async () => {
            vi.mocked(authApi.logout).mockRejectedValue(new Error('Network error'))

            await store.logout()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
        })

        it('clears local state before the server logout request finishes', async () => {
            const pendingLogout = createDeferred<Awaited<ReturnType<typeof authApi.logout>>>()
            vi.mocked(authApi.logout).mockReturnValue(pendingLogout.promise)

            const logout = store.logout()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(mockSessionBoundary).toHaveBeenCalledWith(store.sessionGeneration)
            expect(detachBrowserPushSubscriptionForSession).toHaveBeenCalledWith('token')

            pendingLogout.resolve(authLogoutResponse())
            await logout
        })

        it('keeps the session when explicit logout cleanup is cancelled', async () => {
            mockExplicitLogout.mockResolvedValueOnce(false)

            await store.logout()

            expect(authApi.logout).not.toHaveBeenCalled()
            expect(store.accessToken).toBe('token')
            expect(store.user).toEqual(authUser())
            expect(getStoredAccessToken()).toBe('token')
        })
    })

    describe('fetchUser', () => {
        it('does nothing if no token', async () => {
            store.accessToken = null
            const result = await store.fetchUser()

            expect(result).toBe(false)
            expect(authApi.getMe).not.toHaveBeenCalled()
        })

        it('fetches user successfully', async () => {
            persistAccessToken('token')
            store.accessToken = 'token'
            const mockUser = authUser()
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(mockUser))

            const result = await store.fetchUser()

            expect(result).toBe(true)
            expect(store.user).toEqual(mockUser)
        })

        it('hydrates access token from storage and syncs theme', async () => {
            persistAccessToken('stored-token')
            store.accessToken = null
            const mockUser = authUser({ theme: 'DARK' })
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(mockUser))

            const result = await store.fetchUser({ headers: { 'x-test': '1' } })

            expect(result).toBe(true)
            expect(store.accessToken).toBe('stored-token')
            expect(authApi.getMe).toHaveBeenCalledWith({ headers: { 'x-test': '1' } })
            expect(store.user).toEqual(mockUser)
            expect(mockSyncThemeFromUser).toHaveBeenCalledWith(mockUser)
        })

        it('returns false when getMe response is unsuccessful', async () => {
            persistAccessToken('token')
            store.accessToken = 'token'
            vi.mocked(authApi.getMe).mockResolvedValue(authUserFailureResponse())

            const result = await store.fetchUser()

            expect(result).toBe(false)
            expect(store.user).toBeNull()
        })

        it('does not logout on fetch error (handled by interceptor)', async () => {
            persistAccessToken('token')
            store.accessToken = 'token'
            vi.mocked(authApi.getMe).mockRejectedValue(new Error('Invalid token'))

            const result = await store.fetchUser()

            // fetchUser now only logs the error, interceptor handles logout
            // Token should remain as interceptor is mocked
            expect(result).toBe(false)
            expect(store.accessToken).toBe('token')
            expect(logger.error).toHaveBeenCalledWith('Fetch user failed:', expect.any(Error))
        })
    })

    describe('setTokens', () => {
        it('updates reactive token and removes stale refresh tokens', () => {
            localStorage.setItem('refreshToken', 'stale-refresh')
            store.setTokens('new-access')

            expect(store.accessToken).toBe('new-access')
            expect(getStoredAccessToken()).toBe('new-access')
            expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
            expect(localStorage.getItem('refreshToken')).toBeNull()
        })
    })

    describe('bootstrapSession', () => {
        it('deduplicates concurrent refresh-cookie bootstrap calls', async () => {
            const user = authUser()
            const pendingRefresh = createDeferred<Awaited<ReturnType<typeof authApi.refreshToken>>>()
            vi.mocked(authApi.refreshToken).mockReturnValue(pendingRefresh.promise)
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(user))

            const first = store.bootstrapSession()
            const second = store.bootstrapSession()
            pendingRefresh.resolve(authLoginResponse(user, 'boot-token'))

            await expect(Promise.all([first, second])).resolves.toEqual([true, true])
            expect(authApi.refreshToken).toHaveBeenCalledTimes(1)
        })

        it('attempts refresh-cookie bootstrap only once until the session changes', async () => {
            vi.mocked(authApi.refreshToken)
                .mockRejectedValueOnce(new Error('refresh failed'))
                .mockResolvedValueOnce(authLoginFailureResponse(authUser(), 'ignored'))

            await expect(store.bootstrapSession()).resolves.toBe(false)
            await expect(store.bootstrapSession()).resolves.toBe(false)
            expect(authApi.refreshToken).toHaveBeenCalledTimes(1)

            store.clearSessionState()
            await expect(store.bootstrapSession()).resolves.toBe(false)
            expect(authApi.refreshToken).toHaveBeenCalledTimes(2)
        })

        it('retries a transient bootstrap failure after the cooldown', async () => {
            const now = vi.spyOn(Date, 'now').mockReturnValue(1000)
            vi.mocked(authApi.refreshToken)
                .mockRejectedValueOnce({ response: { status: 503 } })
                .mockRejectedValueOnce({ response: { status: 503 } })

            await expect(store.bootstrapSession()).resolves.toBe(false)
            await expect(store.bootstrapSession()).resolves.toBe(false)
            expect(authApi.refreshToken).toHaveBeenCalledTimes(1)

            now.mockReturnValue(4001)
            await expect(store.bootstrapSession()).resolves.toBe(false)
            expect(authApi.refreshToken).toHaveBeenCalledTimes(2)
            now.mockRestore()
        })

        it('does not retry a terminal bootstrap authentication failure', async () => {
            vi.mocked(authApi.refreshToken).mockRejectedValue({ response: { status: 401 } })

            await expect(store.bootstrapSession()).resolves.toBe(false)
            await expect(store.bootstrapSession()).resolves.toBe(false)

            expect(authApi.refreshToken).toHaveBeenCalledTimes(1)
        })

        it('restores a session from refresh cookie and fetches the user', async () => {
            const user = authUser({ theme: 'DARK' })
            vi.mocked(authApi.refreshToken).mockResolvedValue(authLoginResponse(user, 'boot-token'))
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(user))

            const result = await store.bootstrapSession()

            expect(result).toBe(true)
            expect(authApi.refreshToken).toHaveBeenCalledWith({
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
                signal: expect.any(AbortSignal),
            })
            expect(authApi.getMe).toHaveBeenCalledWith({
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            })
            expect(store.accessToken).toBe('boot-token')
            expect(getStoredAccessToken()).toBe('boot-token')
            expect(store.user).toEqual(user)
            expect(mockSyncThemeFromUser).toHaveBeenCalledWith(user)
        })

        // 부트스트랩은 같은 사용자가 토큰만 새로 받는 것이지 주체 변경이 아니다.
        // 여기서 주체 변경으로 처리하면, 계정 설정에서 온 기기 저장값(표시 시간대 등)이
        // 새로고침마다 지워져 설정 응답이 도착하기 전까지 잘못된 값으로 그려진다.
        it('does not report a principal change when bootstrapping an existing session', async () => {
            const user = authUser()
            vi.mocked(authApi.refreshToken).mockResolvedValue(authLoginResponse(user, 'boot-token'))
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(user))

            await expect(store.bootstrapSession()).resolves.toBe(true)

            expect(mockSessionBoundary).toHaveBeenCalled()
            expect(mockPrincipalChange).not.toHaveBeenCalled()
        })

        it('reports a principal change on login and on logout', async () => {
            const user = authUser()
            vi.mocked(authApi.login).mockResolvedValue(authLoginResponse(user, 'login-token'))

            await store.login({ loginId: 'tester', password: 'pw' })
            expect(mockPrincipalChange).toHaveBeenCalledTimes(1)

            store.clearSessionState()
            expect(mockPrincipalChange).toHaveBeenCalledTimes(2)
        })

        it('clears state when refresh response is unsuccessful', async () => {
            store.accessToken = null
            store.user = authUser()
            persistAccessToken('stale')
            vi.mocked(authApi.refreshToken).mockResolvedValue(authLoginFailureResponse(authUser(), 'ignored'))

            const result = await store.bootstrapSession()

            expect(result).toBe(false)
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
            expect(authApi.getMe).not.toHaveBeenCalled()
        })

        it('preserves local session context when refresh throws transiently', async () => {
            store.accessToken = null
            store.user = authUser()
            persistAccessToken('stale')
            const error = new Error('refresh failed')
            vi.mocked(authApi.refreshToken).mockRejectedValue(error)

            const result = await store.bootstrapSession()

            expect(result).toBe(false)
            expect(store.accessToken).toBeNull()
            expect(store.user).toEqual(expect.objectContaining({ userId: 1 }))
            expect(getStoredAccessToken()).toBe('stale')
            expect(store.bootstrapState).toBe('transient-error')
            expect(logger.error).toHaveBeenCalledWith('Bootstrap session failed:', error)
        })
    })

    describe('storage synchronization', () => {
        it('hydrates state from a token changed in another tab', async () => {
            persistAccessToken('external-token')
            const mockUser = authUser({ userId: 2, loginId: 'synced', displayName: 'Synced User', theme: 'DARK' })
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(mockUser))

            const result = await store.syncFromStoredAccessToken('external-token')

            expect(result).toBe(true)
            expect(store.accessToken).toBe('external-token')
            expect(store.user).toEqual(mockUser)
            expect(authApi.getMe).toHaveBeenCalledWith({ skipAuthRefresh: true })
            expect(mockSyncThemeFromUser).toHaveBeenCalledWith(mockUser)
        })

        it('clears reactive state when another tab removes the token', async () => {
            store.accessToken = 'token'
            store.user = authUser()

            const result = await store.syncFromStoredAccessToken(null)

            expect(result).toBe(false)
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(authApi.logout).not.toHaveBeenCalled()
        })

        it('registers a storage event listener for cross-tab logout', () => {
            store.accessToken = 'token'
            store.user = authUser()
            const stop = registerAuthStorageSync(store)

            window.dispatchEvent(new StorageEvent('storage', {
                key: AUTH_SESSION_EVENT_KEY,
                newValue: `${AUTH_SESSION_CLEARED_EVENT_PREFIX}123`,
                storageArea: localStorage,
            }))

            stop()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(authApi.logout).not.toHaveBeenCalled()
        })

        it('keeps the refreshed token and exposes a retryable state when profile hydration is temporarily unavailable', async () => {
            vi.mocked(authApi.refreshToken).mockResolvedValue(authLoginResponse(authUser(), 'boot-token'))
            vi.mocked(authApi.getMe).mockRejectedValue({ response: { status: 503 } })

            await expect(store.bootstrapSession()).resolves.toBe(false)

            expect(store.accessToken).toBe('boot-token')
            expect(getStoredAccessToken()).toBe('boot-token')
            expect(store.user).toBeNull()
            expect(store.bootstrapState).toBe('transient-error')
        })

        it('does not broadcast a logout boundary when bootstrap refresh fails transiently', async () => {
            const storageSpy = vi.spyOn(Storage.prototype, 'setItem')
            vi.mocked(authApi.refreshToken).mockRejectedValue({ response: { status: 503 } })

            await expect(store.bootstrapSession()).resolves.toBe(false)

            expect(store.bootstrapState).toBe('transient-error')
            expect(storageSpy).not.toHaveBeenCalledWith(
                AUTH_SESSION_EVENT_KEY,
                expect.stringContaining(AUTH_SESSION_CLEARED_EVENT_PREFIX),
            )
        })

        it('retries only profile hydration after refresh has already succeeded', async () => {
            const user = authUser()
            vi.mocked(authApi.refreshToken).mockResolvedValue(authLoginResponse(user, 'boot-token'))
            vi.mocked(authApi.getMe)
                .mockRejectedValueOnce({ response: { status: 503 } })
                .mockResolvedValueOnce(authUserResponse(user))

            await expect(store.bootstrapSession()).resolves.toBe(false)
            await expect(store.retryBootstrapSession()).resolves.toBe(true)

            expect(authApi.refreshToken).toHaveBeenCalledTimes(1)
            expect(authApi.getMe).toHaveBeenCalledTimes(2)
            expect(store.user).toEqual(user)
            expect(store.bootstrapState).toBe('authenticated')
        })

        it('terminates the refreshed session when profile hydration rejects authentication', async () => {
            vi.mocked(authApi.refreshToken).mockResolvedValue(authLoginResponse(authUser(), 'boot-token'))
            vi.mocked(authApi.getMe).mockRejectedValue({ response: { status: 401 } })

            await expect(store.bootstrapSession()).resolves.toBe(false)

            expect(store.accessToken).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
            expect(store.bootstrapState).toBe('terminal-error')
        })

        it('applies a typed cross-tab login boundary through the normal session cleanup path', () => {
            store.accessToken = 'account-a-token'
            store.user = authUser({ userId: 1 })
            const previousGeneration = store.sessionGeneration
            const stop = registerAuthStorageSync(store)

            window.dispatchEvent(new StorageEvent('storage', {
                key: 'noviisAuthBoundaryEvent',
                newValue: JSON.stringify({
                    type: 'auth-boundary',
                    boundary: 'account-switch',
                    eventId: 'peer-boundary',
                    sourceId: 'peer-tab',
                    at: Date.now(),
                }),
                storageArea: localStorage,
            }))

            stop()
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(store.sessionGeneration).toBe(previousGeneration + 1)
            expect(mockSessionBoundary).toHaveBeenCalledWith(store.sessionGeneration)
            expect(authApi.logout).not.toHaveBeenCalled()
        })

        it('broadcasts a typed clear event for cross-tab logout', () => {
            localStorage.removeItem(AUTH_SESSION_EVENT_KEY)

            clearStoredAuthTokens()

            expect(localStorage.getItem(AUTH_SESSION_EVENT_KEY))
                .toMatch(new RegExp(`^${AUTH_SESSION_CLEARED_EVENT_PREFIX}\\d+$`))
        })
    })

    describe('clearSessionState', () => {
        it('clears reactive auth state and stored tokens without calling logout api', () => {
            store.accessToken = 'token'
            store.user = authUser()
            persistAccessToken('token')
            localStorage.setItem('refreshToken', 'stale-refresh')

            store.clearSessionState()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
            expect(localStorage.getItem('refreshToken')).toBeNull()
            expect(authApi.logout).not.toHaveBeenCalled()
        })
    })

    it('clears the session when refresh hydration resolves to a different user identity', async () => {
        store.accessToken = 'rotated-token'
        store.user = authUser({ userId: 1 })
        vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(authUser({ userId: 2 })))

        await expect(store.fetchUser({ skipAuthRefresh: true }, 1)).resolves.toBe(false)

        expect(store.accessToken).toBeNull()
        expect(store.user).toBeNull()
    })

    describe('getters', () => {
        it('isAdmin returns correct value', () => {
            store.user = authUser({ role: 'ADMIN' })
            expect(store.isAdmin).toBe(true)

            store.user = authUser({ role: 'USER' })
            expect(store.isAdmin).toBe(false)

            store.user = null
            expect(store.isAdmin).toBe(false)
        })
    })
})
