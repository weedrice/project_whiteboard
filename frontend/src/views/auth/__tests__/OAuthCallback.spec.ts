import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OAuthCallback from '../OAuthCallback.vue'
import { closeAuthRefreshCoordinatorForTest } from '@/api/authRefreshCoordinator'
import { saveLoginRedirect } from '@/utils/authRedirect'

const mocks = vi.hoisted(() => {
    const router = {
        push: vi.fn(),
        replace: vi.fn(),
    }
    const route = {
        query: {} as Record<string, unknown>,
    }
    const authStore = {
        accessToken: null as string | null,
        sessionGeneration: 0,
        setTokens: vi.fn(),
        applyNewSessionIfCurrent: vi.fn((generation: number, previousToken: string | null, token: string) => {
            if (authStore.sessionGeneration !== generation || authStore.accessToken !== previousToken) return false
            authStore.sessionGeneration += 1
            authStore.accessToken = token
            return true
        }),
        fetchUser: vi.fn(),
        hydrateUser: vi.fn(),
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

vi.mock('@/api/authRefreshCoordinator', () => ({
    coordinateAuthRefresh: (
        refresh: (signal: AbortSignal) => Promise<string>,
        options: { signal?: AbortSignal } = {},
    ) => {
        const controller = new AbortController()
        options.signal?.addEventListener('abort', () => controller.abort(), { once: true })
        return refresh(controller.signal)
    },
    closeAuthRefreshCoordinatorForTest: vi.fn(),
}))

const flushMountedWork = async () => {
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
    await new Promise((resolve) => setTimeout(resolve, 60))
}

describe('OAuthCallback', () => {
    beforeEach(() => {
        closeAuthRefreshCoordinatorForTest()
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
        mocks.authStore.hydrateUser.mockResolvedValue('success')
        mocks.authStore.accessToken = null
        mocks.authStore.sessionGeneration = 0
        mocks.authStore.logout.mockImplementation(() => {
            mocks.authStore.sessionGeneration += 1
            mocks.authStore.accessToken = null
            return Promise.resolve()
        })
        window.history.replaceState({}, '', '/auth/oauth/callback')
    })

    it('exchanges the refresh cookie, stores token, fetches user and redirects to safe saved path', async () => {
        const replaceStateSpy = vi.spyOn(window.history, 'replaceState')
        saveLoginRedirect('/boards')
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
            signal: expect.any(AbortSignal),
        })
        expect(mocks.authStore.applyNewSessionIfCurrent).toHaveBeenCalledWith(0, null, 'refreshed-access')
        expect(mocks.authStore.hydrateUser).toHaveBeenCalledWith({ skipAuthRefresh: true })
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

        expect(mocks.authStore.applyNewSessionIfCurrent).toHaveBeenCalledWith(0, null, 'refreshed-access')
        expect(window.location.hash).toBe('#state=abc')
        expect(replaceStateSpy).toHaveBeenCalled()
    })

    it('ignores query token values and uses the refresh result', async () => {
        mocks.route.query = {
            accessToken: ['array-access'],
        }

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.applyNewSessionIfCurrent).toHaveBeenCalledWith(0, null, 'refreshed-access')
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

    it('does not log out a newer session when an obsolete callback fails', async () => {
        let rejectRefresh!: (error: Error) => void
        mocks.authApi.refreshToken.mockReturnValueOnce(new Promise((_resolve, reject) => {
            rejectRefresh = reject
        }))

        mount(OAuthCallback)
        await Promise.resolve()
        mocks.authStore.sessionGeneration = 1
        mocks.authStore.accessToken = 'account-b-access'
        rejectRefresh(new Error('obsolete oauth callback failed'))
        await flushMountedWork()

        expect(mocks.authStore.logout).not.toHaveBeenCalled()
        expect(mocks.toastStore.addToast).not.toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).not.toHaveBeenCalledWith('/login')
        expect(mocks.authStore.accessToken).toBe('account-b-access')
    })

    it('does not redirect when another account becomes current during user hydration', async () => {
        let resolveHydration!: (value: 'success') => void
        mocks.authStore.hydrateUser.mockReturnValueOnce(new Promise((resolve) => {
            resolveHydration = resolve
        }))

        mount(OAuthCallback)
        await vi.waitFor(() => expect(mocks.authStore.hydrateUser).toHaveBeenCalledTimes(1))
        mocks.authStore.sessionGeneration += 1
        mocks.authStore.accessToken = 'account-b-access'
        resolveHydration('success')
        await flushMountedWork()

        expect(mocks.toastStore.addToast).not.toHaveBeenCalledWith('auth.loginSuccess', 'success')
        expect(mocks.router.push).not.toHaveBeenCalled()
        expect(mocks.authStore.logout).not.toHaveBeenCalled()
    })

    it('does not redirect a newer session after an obsolete logout request finishes', async () => {
        let resolveLogout!: () => void
        mocks.authApi.refreshToken.mockRejectedValueOnce(new Error('failed'))
        mocks.authStore.logout.mockImplementationOnce(() => {
            mocks.authStore.sessionGeneration += 1
            mocks.authStore.accessToken = null
            return new Promise<void>((resolve) => {
                resolveLogout = resolve
            })
        })

        mount(OAuthCallback)
        await vi.waitFor(() => expect(mocks.authStore.logout).toHaveBeenCalledTimes(1))
        mocks.authStore.sessionGeneration += 1
        mocks.authStore.accessToken = 'account-b-access'
        resolveLogout()
        await flushMountedWork()

        expect(mocks.toastStore.addToast).not.toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).not.toHaveBeenCalledWith('/login')
        expect(mocks.authStore.accessToken).toBe('account-b-access')
    })

    it('aborts the callback without logging out when the view is unmounted', async () => {
        mocks.authApi.refreshToken.mockImplementationOnce(({ signal }: { signal: AbortSignal }) => (
            new Promise((_resolve, reject) => {
                signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true })
            })
        ))

        const wrapper = mount(OAuthCallback)
        await Promise.resolve()
        wrapper.unmount()
        await flushMountedWork()

        expect(mocks.authStore.logout).not.toHaveBeenCalled()
        expect(mocks.router.push).not.toHaveBeenCalledWith('/login')
    })

    it('redirects to login when user hydration is terminal', async () => {
        mocks.authStore.hydrateUser.mockResolvedValueOnce('terminal-failure')

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.applyNewSessionIfCurrent).toHaveBeenCalledWith(0, null, 'refreshed-access')
        expect(mocks.authStore.logout).toHaveBeenCalled()
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.replace).toHaveBeenCalledWith('/login')
    })

    it('preserves the OAuth session and opens retry UI when user hydration is transient', async () => {
        mocks.authStore.hydrateUser.mockResolvedValueOnce('transient-failure')

        mount(OAuthCallback)
        await flushMountedWork()

        expect(mocks.authStore.logout).not.toHaveBeenCalled()
        expect(mocks.authStore.accessToken).toBe('refreshed-access')
        expect(mocks.router.replace).toHaveBeenCalledWith({
            name: 'error',
            query: { status: '503', retry: '/' },
        })
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

        expect(mocks.authStore.applyNewSessionIfCurrent).not.toHaveBeenCalled()
        expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
        expect(mocks.router.push).toHaveBeenCalledWith('/login')
        expect(replaceStateSpy.mock.calls.length).toBe(replaceStateCallCountBeforeMount)
    })
})
