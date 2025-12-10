import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'
import { authApi } from '@/api/auth'
import router from '@/router'
import { useThemeStore } from '@/stores/theme'

// Mock dependencies
vi.mock('@/api/auth', () => ({
    authApi: {
        login: vi.fn(),
        logout: vi.fn(),
        getMe: vi.fn()
    }
}))

vi.mock('@/router', () => ({
    default: {
        push: vi.fn()
    }
}))

const mockSetTheme = vi.fn()
vi.mock('@/stores/theme', () => ({
    useThemeStore: vi.fn(() => ({
        setTheme: mockSetTheme
    }))
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
                        refreshToken: 'refresh-token',
                        user: { id: 1, username: 'test', role: 'USER', theme: 'DARK' }
                    }
                }
            }
            vi.mocked(authApi.login).mockResolvedValue(mockResponse as any)

            const result = await store.login({ username: 'test', password: 'password' })

            expect(result).toBe(true)
            expect(store.accessToken).toBe('new-token')
            expect(store.user).toEqual(mockResponse.data.data.user)
            expect(localStorage.getItem('accessToken')).toBe('new-token')
            expect(localStorage.getItem('refreshToken')).toBe('refresh-token')

            // Verify theme setting
            expect(mockSetTheme).toHaveBeenCalledWith('DARK')
        })

        it('handles login failure', async () => {
            const error = new Error('Login failed')
            vi.mocked(authApi.login).mockRejectedValue(error)

            await expect(store.login({ username: 'test', password: 'wrong' })).rejects.toThrow('Login failed')
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
        })
    })

    describe('logout', () => {
        beforeEach(() => {
            store.accessToken = 'token'
            store.user = { id: 1, username: 'test', role: 'USER' } as any
            localStorage.setItem('accessToken', 'token')
            localStorage.setItem('refreshToken', 'refresh-token')
        })

        it('handles successful logout', async () => {
            vi.mocked(authApi.logout).mockResolvedValue({ data: { success: true } } as any)

            await store.logout()

            expect(authApi.logout).toHaveBeenCalledWith('refresh-token')
            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(localStorage.getItem('accessToken')).toBeNull()
            expect(localStorage.getItem('refreshToken')).toBeNull()
            expect(router.push).toHaveBeenCalledWith('/login')
        })

        it('cleans up state even if api call fails', async () => {
            vi.mocked(authApi.logout).mockRejectedValue(new Error('Network error'))

            await store.logout()

            expect(store.accessToken).toBeNull()
            expect(store.user).toBeNull()
            expect(router.push).toHaveBeenCalledWith('/login')
        })
    })

    describe('fetchUser', () => {
        it('does nothing if no token', async () => {
            store.accessToken = null
            await store.fetchUser()
            expect(authApi.getMe).not.toHaveBeenCalled()
        })

        it('fetches user successfully', async () => {
            store.accessToken = 'token'
            const mockUser = { id: 1, username: 'test', role: 'USER' }
            vi.mocked(authApi.getMe).mockResolvedValue({
                data: { success: true, data: mockUser }
            } as any)

            await store.fetchUser()

            expect(store.user).toEqual(mockUser)
        })

        it('handles sanctioned user', async () => {
            store.accessToken = 'token'
            const mockUser = { id: 1, username: 'test', role: 'USER', status: 'SANCTIONED' }
            vi.mocked(authApi.getMe).mockResolvedValue({
                data: { success: true, data: mockUser }
            } as any)

            // Mock alert
            const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => { })

            await store.fetchUser()

            expect(alertMock).toHaveBeenCalled()
            expect(store.accessToken).toBeNull() // Should have logged out
            expect(router.push).toHaveBeenCalledWith('/login')
        })

        it('logs out on fetch error', async () => {
            store.accessToken = 'token'
            vi.mocked(authApi.getMe).mockRejectedValue(new Error('Invalid token'))

            await store.fetchUser()

            expect(store.accessToken).toBeNull()
            expect(router.push).toHaveBeenCalledWith('/login')
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
