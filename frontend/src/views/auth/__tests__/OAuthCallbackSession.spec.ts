import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OAuthCallback from '../OAuthCallback.vue'
import { authApi } from '@/api/auth'
import { configureAuthSessionEffects, useAuthStore } from '@/stores/auth'
import { clearStoredAuthTokens } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'
import { authLogoutResponse, authUser, authUserResponse } from '@/stores/__tests__/storeTestFixtures'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'

const mocks = vi.hoisted(() => ({
    router: { push: vi.fn(), replace: vi.fn() },
    addToast: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRouter: () => mocks.router }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn() } }))
vi.mock('@/api/auth', () => ({
    authApi: { login: vi.fn(), logout: vi.fn(), refreshToken: vi.fn(), getMe: vi.fn() },
}))
vi.mock('@/features/notifications/pushSubscriptions', () => ({
    detachBrowserPushSubscriptionForSession: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/api/authRefreshCoordinator', () => ({
    coordinateAuthRefresh: (refresh: (signal: AbortSignal) => Promise<string>) => (
        refresh(new AbortController().signal)
    ),
    rotateSharedAuthSessionId: vi.fn(),
    cancelAuthRefreshCoordinator: vi.fn(),
}))

describe('OAuthCallback with the auth store hydration contract', () => {
    let wrapper: VueWrapper | undefined
    let store: ReturnType<typeof useAuthStore>

    beforeEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        sessionStorage.clear()
        clearStoredAuthTokens(false)
        configureAuthSessionEffects({})
        setActivePinia(createPinia())
        store = useAuthStore()
        vi.mocked(authApi.refreshToken).mockResolvedValue(apiSuccessDataResponse<typeof authApi.refreshToken>({
            accessToken: 'oauth-access', expiresIn: 1800,
        }))
        vi.mocked(authApi.logout).mockResolvedValue(authLogoutResponse())
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
        vi.restoreAllMocks()
    })

    async function startHydration() {
        const response = createDeferred<Awaited<ReturnType<typeof authApi.getMe>>>()
        vi.mocked(authApi.getMe).mockReturnValueOnce(response.promise)
        wrapper = mount(OAuthCallback)
        await flushPromises()
        expect(authApi.getMe).toHaveBeenCalledTimes(1)
        expect(store.accessToken).toBe('oauth-access')
        return response
    }

    it.each(['success', 401, 403] as const)(
        'preserves the newer session when obsolete hydration finishes with %s',
        async (outcome) => {
            const response = await startHydration()
            store.setTokens('new-session-access')
            const generation = store.sessionGeneration
            if (outcome === 'success') response.resolve(authUserResponse(authUser()))
            else response.reject({ response: { status: outcome } })
            await flushPromises()

            expect(store.accessToken).toBe('new-session-access')
            expect(store.sessionGeneration).toBe(generation)
            expect(authApi.logout).not.toHaveBeenCalled()
            expect(mocks.addToast).not.toHaveBeenCalled()
            expect(mocks.router.push).not.toHaveBeenCalled()
            expect(mocks.router.replace).not.toHaveBeenCalled()
        },
    )

    it('redirects after terminal hydration has cleared its own session', async () => {
        const response = await startHydration()
        const generation = store.sessionGeneration
        response.reject({ response: { status: 401 } })
        await flushPromises()

        expect(store.accessToken).toBeNull()
        expect(store.sessionGeneration).toBe(generation + 2)
        expect(authApi.logout).toHaveBeenCalledTimes(1)
        expect(mocks.router.replace).toHaveBeenCalledWith('/login')
        expect(mocks.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error')
    })

    it('preserves a session established after terminal hydration clears the old session', async () => {
        const hydrateUser = store.hydrateUser.bind(store)
        vi.spyOn(store, 'hydrateUser').mockImplementation(async (...args) => {
            const result = await hydrateUser(...args)
            store.setTokens('new-session-access')
            return result
        })
        const response = await startHydration()
        response.reject({ response: { status: 401 } })
        await flushPromises()

        expect(store.accessToken).toBe('new-session-access')
        expect(authApi.logout).not.toHaveBeenCalled()
        expect(mocks.addToast).not.toHaveBeenCalled()
        expect(mocks.router.replace).not.toHaveBeenCalled()
    })

    it('does not redirect a newer session while terminal logout is awaiting the server', async () => {
        const logout = createDeferred<Awaited<ReturnType<typeof authApi.logout>>>()
        vi.mocked(authApi.logout).mockReturnValueOnce(logout.promise)
        const response = await startHydration()
        response.reject({ response: { status: 403 } })
        await flushPromises()
        expect(authApi.logout).toHaveBeenCalledTimes(1)

        store.setTokens('new-session-access')
        logout.resolve(authLogoutResponse())
        await flushPromises()

        expect(store.accessToken).toBe('new-session-access')
        expect(mocks.addToast).not.toHaveBeenCalled()
        expect(mocks.router.replace).not.toHaveBeenCalled()
    })

    it('does not initiate logout or navigation after unmount during terminal hydration', async () => {
        const response = await startHydration()
        wrapper?.unmount()
        wrapper = undefined
        response.reject({ response: { status: 401 } })
        await flushPromises()

        expect(authApi.logout).not.toHaveBeenCalled()
        expect(mocks.addToast).not.toHaveBeenCalled()
        expect(mocks.router.replace).not.toHaveBeenCalled()
    })

    it('keeps the session and offers retry after a transient hydration failure', async () => {
        const response = await startHydration()
        response.reject({ response: { status: 503 } })
        await flushPromises()

        expect(store.accessToken).toBe('oauth-access')
        expect(authApi.logout).not.toHaveBeenCalled()
        expect(mocks.router.replace).toHaveBeenCalledWith({
            name: 'error', query: { status: '503', retry: '/' },
        })
    })
})
