import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { configureAuthSessionEffects, registerAuthStorageSync, useAuthStore } from '../auth'
import { authApi } from '@/api/auth'
import logger from '@/utils/logger'
import { AUTH_SESSION_EVENT_KEY, clearStoredAuthTokens, getStoredAccessToken, persistAccessToken } from '@/utils/authTokenStorage'
import {
    authLoginFailureResponse,
    authLoginResponse,
    authLogoutResponse,
    authUser,
    authUserFailureResponse,
    authUserResponse,
} from './storeTestFixtures'

// Mock dependencies
vi.mock('@/api/auth', () => ({
    authApi: {
        login: vi.fn(),
        logout: vi.fn(),
        getMe: vi.fn()
    }
}))

const mockSyncThemeFromUser = vi.fn()
const mockHandleSanctionedSession = vi.fn()

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
    }
}))

describe('Auth Store', () => {
    let store: ReturnType<typeof useAuthStore>

    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
        clearStoredAuthTokens()
        vi.clearAllMocks()
        configureAuthSessionEffects({
            syncThemeFromUser: mockSyncThemeFromUser,
            handleSanctionedSession: mockHandleSanctionedSession,
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
            const user = authUser({ theme: 'DARK' })
            vi.mocked(authApi.login).mockResolvedValue(authLoginResponse(user))

            const result = await store.login({ loginId: 'test', password: 'password' })

            expect(result).toBe(true)
            expect(store.accessToken).toBe('new-token')
            expect(store.user).toEqual(user)
            expect(getStoredAccessToken()).toBe('new-token')
            expect(localStorage.getItem('refreshToken')).toBeNull()

            expect(mockSyncThemeFromUser).toHaveBeenCalledWith(user)
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
    })

    describe('logout', () => {
        beforeEach(() => {
            store.accessToken = 'token'
            store.user = authUser()
            persistAccessToken('token')
        })

        it('handles successful logout', async () => {
            vi.mocked(authApi.logout).mockResolvedValue(authLogoutResponse())

            await store.logout()

            expect(authApi.logout).toHaveBeenCalled()
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(getStoredAccessToken()).toBeNull()
            expect(localStorage.getItem('refreshToken')).toBeNull()
        })

        it('cleans up state even if api call fails', async () => {
            vi.mocked(authApi.logout).mockRejectedValue(new Error('Network error'))

            await store.logout()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
        })
    })

    describe('handleSanctionedSession', () => {
        it('shows sanction toast and clears auth state', async () => {
            store.accessToken = 'token'
            store.user = authUser({ status: 'SANCTIONED' })
            persistAccessToken('token')
            vi.mocked(authApi.logout).mockResolvedValue(authLogoutResponse())

            await store.handleSanctionedSession()

            expect(mockHandleSanctionedSession).toHaveBeenCalled()
            expect(authApi.logout).toHaveBeenCalled()
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
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

        it('handles sanctioned user', async () => {
            persistAccessToken('token')
            store.accessToken = 'token'
            const mockUser = authUser({ status: 'SANCTIONED' })
            vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(mockUser))

            const result = await store.fetchUser()

            expect(result).toBe(false)
            expect(store.accessToken).toBeNull() // Should have logged out
            expect(mockHandleSanctionedSession).toHaveBeenCalled()
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
            expect(localStorage.getItem('refreshToken')).toBeNull()
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
                newValue: null,
                storageArea: localStorage,
            }))

            stop()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
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
