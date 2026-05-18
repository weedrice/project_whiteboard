import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'
import { authApi } from '@/api/auth'
import { useThemeStore } from '@/stores/theme'
import logger from '@/utils/logger'

// Mock dependencies
vi.mock('@/api/auth', () => ({
    authApi: {
        login: vi.fn(),
        logout: vi.fn(),
        getMe: vi.fn()
    }
}))

const mockSetTheme = vi.fn()
vi.mock('@/stores/theme', () => ({
    useThemeStore: vi.fn(() => ({
        setTheme: mockSetTheme
    }))
}))

const mockAddToast = vi.fn()
vi.mock('@/stores/toast', () => ({
    useToastStore: vi.fn(() => ({
        addToast: mockAddToast
    }))
}))

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
        vi.clearAllMocks()
        store = useAuthStore()
    })

    it('initializes with no user', () => {
        expect(store.user).toBeNull()
        expect(store.isAuthenticated).toBe(false)
    })

    it('initializes with token from local storage', () => {
        // Reset Pinia to ensure fresh store initialization
        setActivePinia(createPinia())
        localStorage.setItem('accessToken', 'test-token')
        store = useAuthStore()
        expect(store.accessToken).toBe('test-token')
        expect(store.isAuthenticated).toBe(true)
    })

    describe('login', () => {
        it('handles successful login', async () => {
            const mockResponse = {
                data: {
                    success: true,
                    data: {
                        accessToken: 'new-token',
                        expiresIn: 1800,
                        user: { id: 1, username: 'test', role: 'USER', theme: 'DARK' }
                    }
                }
            }
            vi.mocked(authApi.login).mockResolvedValue(mockResponse as any)

            const result = await store.login({ loginId: 'test', password: 'password' })

            expect(result).toBe(true)
            expect(store.accessToken).toBe('new-token')
            expect(store.user).toEqual(mockResponse.data.data.user)
            expect(localStorage.getItem('accessToken')).toBe('new-token')
            expect(localStorage.getItem('refreshToken')).toBeNull()

            // Verify theme setting
            expect(mockSetTheme).toHaveBeenCalledWith('DARK')
        })

        it('handles login failure', async () => {
            const error = new Error('Login failed')
            vi.mocked(authApi.login).mockRejectedValue(error)

            await expect(store.login({ loginId: 'test', password: 'wrong' })).rejects.toThrow('Login failed')
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
        })

        it('returns undefined and keeps state when success flag is false', async () => {
            vi.mocked(authApi.login).mockResolvedValue({
                data: {
                    success: false,
                    data: {
                        accessToken: 'ignored-token',
                        expiresIn: 1800,
                        user: { id: 1, username: 'ignored', role: 'USER' }
                    }
                }
            } as any)

            const result = await store.login({ loginId: 'test', password: 'password' })

            expect(result).toBeUndefined()
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(localStorage.getItem('accessToken')).toBeNull()
        })
    })

    describe('logout', () => {
        beforeEach(() => {
            store.accessToken = 'token'
            store.user = { id: 1, username: 'test', role: 'USER' } as any
            localStorage.setItem('accessToken', 'token')
        })

        it('handles successful logout', async () => {
            vi.mocked(authApi.logout).mockResolvedValue({ data: { success: true } } as any)

            await store.logout()

            expect(authApi.logout).toHaveBeenCalled()
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(localStorage.getItem('accessToken')).toBeNull()
            expect(localStorage.getItem('refreshToken')).toBeNull()
        })

        it('cleans up state even if api call fails', async () => {
            vi.mocked(authApi.logout).mockRejectedValue(new Error('Network error'))

            await store.logout()

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
            localStorage.setItem('accessToken', 'token')
            store.accessToken = 'token'
            const mockUser = { id: 1, username: 'test', role: 'USER' }
            vi.mocked(authApi.getMe).mockResolvedValue({
                data: { success: true, data: mockUser }
            } as any)

            const result = await store.fetchUser()

            expect(result).toBe(true)
            expect(store.user).toEqual(mockUser)
        })

        it('hydrates access token from storage and syncs theme', async () => {
            localStorage.setItem('accessToken', 'stored-token')
            store.accessToken = null
            const mockUser = { id: 1, username: 'test', role: 'USER', theme: 'DARK' }
            vi.mocked(authApi.getMe).mockResolvedValue({
                data: { success: true, data: mockUser }
            } as any)

            const result = await store.fetchUser({ headers: { 'x-test': '1' } })

            expect(result).toBe(true)
            expect(store.accessToken).toBe('stored-token')
            expect(authApi.getMe).toHaveBeenCalledWith({ headers: { 'x-test': '1' } })
            expect(store.user).toEqual(mockUser)
            expect(mockSetTheme).toHaveBeenCalledWith('DARK')
        })

        it('returns false when getMe response is unsuccessful', async () => {
            localStorage.setItem('accessToken', 'token')
            store.accessToken = 'token'
            vi.mocked(authApi.getMe).mockResolvedValue({
                data: { success: false, data: null }
            } as any)

            const result = await store.fetchUser()

            expect(result).toBe(false)
            expect(store.user).toBeNull()
        })

        it('handles sanctioned user', async () => {
            localStorage.setItem('accessToken', 'token')
            store.accessToken = 'token'
            const mockUser = { id: 1, username: 'test', role: 'USER', status: 'SANCTIONED' }
            vi.mocked(authApi.getMe).mockResolvedValue({
                data: { success: true, data: mockUser }
            } as any)

            // Mock i18n
            vi.mock('@/i18n', () => ({
                default: {
                    global: {
                        t: (key: string) => key
                    }
                }
            }))

            const result = await store.fetchUser()

            // Now uses toast instead of alert, and logout is called
            expect(result).toBe(false)
            expect(store.accessToken).toBeNull() // Should have logged out
        })

        it('does not logout on fetch error (handled by interceptor)', async () => {
            localStorage.setItem('accessToken', 'token')
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
            expect(localStorage.getItem('accessToken')).toBe('new-access')
            expect(localStorage.getItem('refreshToken')).toBeNull()
        })
    })

    describe('getters', () => {
        it('isAdmin returns correct value', () => {
            store.user = { id: 1, role: 'ADMIN' } as any
            expect(store.isAdmin).toBe(true)

            store.user = { id: 1, role: 'USER' } as any
            expect(store.isAdmin).toBe(false)

            store.user = null
            expect(store.isAdmin).toBe(false)
        })
    })
})
