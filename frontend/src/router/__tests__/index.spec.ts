import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import router from '../index'
import { useAuthStore } from '@/stores/auth'

// Mock Auth Store
vi.mock('@/stores/auth', () => ({
    useAuthStore: vi.fn()
}))

// Mock View Components to prevent API calls during routing
vi.mock('@/views/user/MyPage.vue', () => ({ default: { template: '<div>MyPage</div>' } }))
vi.mock('@/views/user/MyPageDashboard.vue', () => ({ default: { template: '<div>MyPageDashboard</div>' } }))
vi.mock('@/views/home/HomeFeed.vue', () => ({ default: { template: '<div>HomeFeed</div>' } }))
vi.mock('@/views/auth/LoginPage.vue', () => ({ default: { template: '<div>LoginPage</div>' } }))
vi.mock('@/views/admin/AdminDashboard.vue', () => ({ default: { template: '<div>AdminDashboard</div>' } }))

describe('Router Navigation Guards', () => {
    let mockAuthStore: any

    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()

        // Setup default auth store mock
        mockAuthStore = {
            user: null,
            accessToken: null,
            isAuthenticated: false,
            fetchUser: vi.fn(),
            logout: vi.fn().mockImplementation(() => {
                mockAuthStore.user = null
                mockAuthStore.isAuthenticated = false
            })
        }
        vi.mocked(useAuthStore).mockReturnValue(mockAuthStore)
    })

    it('redirects to login if requiresAuth and not authenticated', async () => {
        mockAuthStore.isAuthenticated = false
        await router.push('/mypage')
        expect(router.currentRoute.value.name).toBe('login')
        expect(router.currentRoute.value.query.redirect).toBe('/mypage')
    })

    it('allows navigation if requiresAuth and authenticated', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        await router.push('/mypage')
        expect(router.currentRoute.value.name).toBe('mypage')
    })

    it('redirects to home if guestOnly and authenticated', async () => {
        mockAuthStore.isAuthenticated = true
        await router.push('/login')
        expect(router.currentRoute.value.name).toBe('home')
    })

    it('allows navigation if guestOnly and not authenticated', async () => {
        mockAuthStore.isAuthenticated = false
        await router.push('/login')
        expect(router.currentRoute.value.name).toBe('login')
    })

    it('redirects to home if role check fails', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        await router.push('/admin/dashboard')
        expect(router.currentRoute.value.name).toBe('home')
    })

    it('allows navigation if role check passes', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'SUPER_ADMIN' }
        await router.push('/admin/dashboard')
        expect(router.currentRoute.value.name).toBe('AdminDashboard')
    })

    it('fetches user if token exists but user is missing', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = true // Assuming token implies authenticated for this check

        await router.push('/')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
    })

    it('logs out and redirects to login if user is sanctioned', async () => {
        mockAuthStore.user = { status: 'SANCTIONED' }
        mockAuthStore.isAuthenticated = true

        await router.push('/boards')

        expect(mockAuthStore.logout).toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('login')
    })
})
