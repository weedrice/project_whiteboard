import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OAuthCallback from '../OAuthCallback.vue'

const mocks = vi.hoisted(() => {
    const router = {
        push: vi.fn(),
    }
    const route = {
        query: {} as Record<string, unknown>,
    }
    const authStore = {
        setTokens: vi.fn(),
        fetchUser: vi.fn(),
        logout: vi.fn(),
    }
    const toastStore = {
        addToast: vi.fn(),
    }
    const logger = {
        error: vi.fn(),
    }
    const authApi = {
        refreshToken: vi.fn(),
    }

    return {
        router,
        route,
        authStore,
        toastStore,
        logger,
        authApi,
    }
})

vi.mock('vue-router', () => ({
    useRouter: () => mocks.router,
    useRoute: () => mocks.route,
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => mocks.authStore,
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => mocks.toastStore,
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: mocks.logger,
}))

vi.mock('@/api/auth', () => ({
    authApi: mocks.authApi,
}))

const flushMountedWork = async () => {
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
}

describe('OAuthCallback', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        sessionStorage.clear()
        mocks.route.query = {}
        mocks.authApi.refreshToken.mockResolvedValue({
            data: {
                success: true,
                data: {
                    accessToken: 'refreshed-access',
                },
            },
        })
        mocks.authStore.fetchUser.mockResolvedValue(true)
        mocks.authStore.logout.mockResolvedValue(undefined)
        window.history.replaceState({}, '', '/auth/oauth/callback')
    })

    it('exchanges the refresh cookie, stores token, fetches user and redirects to safe saved path', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        sessionStorage.setItem('loginRedirect', '/boards')
        window.history.replaceState(
            {},
            '',
            '/auth/oauth/callback?accessToken=query-access',
        )

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authApi.refreshToken).toHaveBeenCalledWith({
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        })
        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('refreshed-access')
        expect(mocks.authStore.fetchUser).toHaveBeenCalledWith({ skipAuthRefresh: true })
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginSuccess', 'success')
        expect(mocks.router.push).toHaveBeenCalledWith('/boards')
        expect(sessionStorage.getItem('loginRedirect')).toBeNull()
        expect(window.location.search).toBe('')
        expect(replaceStateSpy).toHaveBeenCalled()
    })

    it('falls back to home for unsafe redirect path', async () => {
        sessionStorage.setItem('loginRedirect', '//evil.example')

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.router.push).toHaveBeenCalledWith('/')
    })

    it('cleans sensitive hash params from URL without using them as tokens', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        window.history.replaceState(
            {},
            '',
            '/auth/oauth/callback#accessToken=hash-access&state=abc',
        )
        mocks.route.query = {}

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('refreshed-access')
        expect(window.location.hash).toBe('#state=abc')
        expect(replaceStateSpy).toHaveBeenCalled()
    })

    it('ignores query token values and uses the refresh result', async () => {
        mocks.route.query = {
            accessToken: ['array-access'],
        }

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('refreshed-access')
    })

    it('redirects to login when refresh cookie exchange fails', async () => {
        mocks.authApi.refreshToken.mockRejectedValueOnce(new Error('failed'))

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.logger.error).toHaveBeenCalledWith('OAuth login failed:', expect.any(Error))
        expect(mocks.authStore.logout).toHaveBeenCalled()
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).toHaveBeenCalledWith('/login')
    })

    it('redirects to login when user hydration returns false', async () => {
        mocks.authStore.fetchUser.mockResolvedValueOnce(false)

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('refreshed-access')
        expect(mocks.authStore.logout).toHaveBeenCalled()
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).toHaveBeenCalledWith('/login')
    })

    it('redirects to login when refresh returns an invalid access token', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        mocks.authApi.refreshToken.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    accessToken: '',
                },
            },
        })
        window.history.replaceState({}, '', '/auth/oauth/callback')
        const replaceStateCallCountBeforeMount = replaceStateSpy.mock.calls.length

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).not.toHaveBeenCalled()
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).toHaveBeenCalledWith('/login')
        expect(replaceStateSpy.mock.calls.length).toBe(replaceStateCallCountBeforeMount)
    })
})
