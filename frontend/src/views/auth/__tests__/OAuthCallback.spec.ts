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
    }
    const toastStore = {
        addToast: vi.fn(),
    }
    const logger = {
        error: vi.fn(),
    }

    return {
        router,
        route,
        authStore,
        toastStore,
        logger,
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
        mocks.authStore.fetchUser.mockResolvedValue(undefined)
        window.history.replaceState({}, '', '/auth/oauth/callback')
    })

    it('stores tokens, fetches user and redirects to safe saved path', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        sessionStorage.setItem('loginRedirect', '/boards')
        mocks.route.query = {
            accessToken: 'query-access',
            refreshToken: 'query-refresh',
        }
        window.history.replaceState(
            {},
            '',
            '/auth/oauth/callback?accessToken=query-access&refreshToken=query-refresh',
        )

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('query-access', 'query-refresh')
        expect(mocks.authStore.fetchUser).toHaveBeenCalled()
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginSuccess', 'success')
        expect(mocks.router.push).toHaveBeenCalledWith('/boards')
        expect(sessionStorage.getItem('loginRedirect')).toBeNull()
        expect(window.location.search).toBe('')
        expect(replaceStateSpy).toHaveBeenCalled()
    })

    it('falls back to home for unsafe redirect path', async () => {
        sessionStorage.setItem('loginRedirect', '//evil.example')
        mocks.route.query = {
            accessToken: 'safe-access',
            refreshToken: 'safe-refresh',
        }

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.router.push).toHaveBeenCalledWith('/')
    })

    it('supports hash tokens and cleans sensitive hash params from URL', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        window.history.replaceState(
            {},
            '',
            '/auth/oauth/callback#accessToken=hash-access&refreshToken=hash-refresh&state=abc',
        )
        mocks.route.query = {}

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('hash-access', 'hash-refresh')
        expect(window.location.hash).toBe('#state=abc')
        expect(replaceStateSpy).toHaveBeenCalled()
    })

    it('accepts array query values and uses first token value', async () => {
        mocks.route.query = {
            accessToken: ['array-access'],
            refreshToken: ['array-refresh'],
        }

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.setTokens).toHaveBeenCalledWith('array-access', 'array-refresh')
    })

    it('redirects to login when token processing fails', async () => {
        mocks.route.query = {
            accessToken: 'bad-access',
            refreshToken: 'bad-refresh',
        }
        mocks.authStore.fetchUser.mockRejectedValueOnce(new Error('failed'))

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.logger.error).toHaveBeenCalledWith('OAuth login failed:', expect.any(Error))
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).toHaveBeenCalledWith('/login')
    })

    it('redirects to login when tokens are missing', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        mocks.route.query = {}
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
