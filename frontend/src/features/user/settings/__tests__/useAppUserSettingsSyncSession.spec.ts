import { QueryClient } from '@tanstack/vue-query'
import { flushPromises } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { effectScope, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { userSettingsSessionQueryKey } from '@/features/user/useUser'
import { clearAuthScopedQueries, configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { configureAuthSessionEffects, useAuthStore } from '@/stores/auth'
import { authUser, authUserResponse } from '@/stores/__tests__/storeTestFixtures'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import type { UserSettings } from '@/types/user'
import { clearStoredAuthTokens } from '@/utils/authTokenStorage'
import { useAppUserSettingsSync } from '../useAppUserSettingsSync'

const locale = vi.hoisted(() => ({ setAppLocale: vi.fn().mockResolvedValue(true) }))

vi.mock('@/i18n', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/i18n')>(),
  setAppLocale: locale.setAppLocale,
}))
vi.mock('@/api/auth', () => ({ authApi: { refreshToken: vi.fn(), getMe: vi.fn() } }))
vi.mock('@/api/user', () => ({ userApi: { getUserSettings: vi.fn() } }))
vi.mock('@/utils/logger', () => ({ default: { warn: vi.fn(), error: vi.fn() } }))

const cleanups: Array<() => void> = []
const settingsA: UserSettings = {
  theme: 'DARK', language: 'en', timezone: 'America/New_York', hideNsfw: false, pushEnabled: false,
}
const settingsB: UserSettings = {
  theme: 'LIGHT', language: 'ko', timezone: 'Asia/Seoul', hideNsfw: false, pushEnabled: false,
}
const settingsResponse = (settings: UserSettings) => apiSuccessDataResponse<typeof userApi.getUserSettings>(settings)

function createHarness(authenticated = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore(pinia)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000, gcTime: Infinity } } })
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  configureAuthSessionEffects({
    onSessionBoundary: generation => {
      notifyAuthSessionBoundary(generation)
      clearAuthScopedQueries(client)
    },
  })
  if (authenticated) {
    auth.user = authUser()
    auth.setTokens('token-a')
  }
  const setTheme = vi.fn()
  const scope = effectScope()
  const sync = scope.run(() => useAppUserSettingsSync(auth, { setTheme }, client))!
  cleanups.push(() => {
    scope.stop()
    client.clear()
    disposePinia(pinia)
  })
  return { auth, client, setTheme, sync }
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  sessionStorage.clear()
  clearStoredAuthTokens(false)
  locale.setAppLocale.mockReset().mockResolvedValue(true)
})

afterEach(() => {
  cleanups.splice(0).forEach(cleanup => cleanup())
  configureAuthSessionEffects({})
  configureAuthQueryScope(() => 0)
  clearStoredAuthTokens(false)
})

describe('app settings with real auth and query lifecycles', () => {
  it('waits for bootstrap /me before loading settings and applies the hydrated user preferences', async () => {
    const me = createDeferred<Awaited<ReturnType<typeof authApi.getMe>>>()
    const settings = createDeferred<Awaited<ReturnType<typeof userApi.getUserSettings>>>()
    vi.mocked(authApi.refreshToken).mockResolvedValue(apiSuccessDataResponse<typeof authApi.refreshToken>({ accessToken: 'bootstrapped-token' }))
    vi.mocked(authApi.getMe).mockReturnValue(me.promise)
    vi.mocked(userApi.getUserSettings).mockReturnValue(settings.promise)
    const { auth, client, setTheme, sync } = createHarness(false)

    const bootstrap = auth.bootstrapSession()
    await vi.waitFor(() => expect(authApi.getMe).toHaveBeenCalledOnce())
    await nextTick()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user).toBeNull()
    await sync.loadSettings()
    expect(userApi.getUserSettings).not.toHaveBeenCalled()

    me.resolve(authUserResponse(authUser()))
    await bootstrap
    await flushPromises()
    expect(userApi.getUserSettings).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      expectedSession: { sessionGeneration: auth.sessionGeneration, userId: 1 },
      signal: expect.any(AbortSignal),
    }))
    settings.resolve(settingsResponse(settingsA))
    await flushPromises()

    expect(setTheme).toHaveBeenCalledExactlyOnceWith('DARK')
    expect(locale.setAppLocale).toHaveBeenLastCalledWith('en', expect.any(Function))
    expect(localStorage.getItem('displayTimeZone')).toBe('America/New_York')
    expect(client.getQueryState(userSettingsSessionQueryKey(auth.sessionGeneration))?.status).toBe('success')
  })

  it('does not apply or reuse cached settings when the known user changes within the same generation', async () => {
    const nextSettings = createDeferred<Awaited<ReturnType<typeof userApi.getUserSettings>>>()
    vi.mocked(userApi.getUserSettings).mockReturnValue(nextSettings.promise)
    const { auth, client, setTheme, sync } = createHarness()
    client.setQueryData(userSettingsSessionQueryKey(auth.sessionGeneration), settingsA)

    const previousLoad = sync.loadSettings()
    auth.user = authUser({ userId: 2 })
    await previousLoad
    await flushPromises()
    expect(setTheme).not.toHaveBeenCalledWith('DARK')
    expect(localStorage.getItem('displayTimeZone')).toBeNull()
    expect(userApi.getUserSettings).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      expectedSession: { sessionGeneration: auth.sessionGeneration, userId: 2 },
    }))

    nextSettings.resolve(settingsResponse(settingsB))
    await flushPromises()
    expect(setTheme).toHaveBeenCalledExactlyOnceWith('LIGHT')
    expect(localStorage.getItem('displayTimeZone')).toBe('Asia/Seoul')
  })

  it('ignores a delayed previous account response while loading the next account settings', async () => {
    const previousSettings = createDeferred<Awaited<ReturnType<typeof userApi.getUserSettings>>>()
    const nextSettings = createDeferred<Awaited<ReturnType<typeof userApi.getUserSettings>>>()
    vi.mocked(userApi.getUserSettings)
      .mockReturnValueOnce(previousSettings.promise)
      .mockReturnValueOnce(nextSettings.promise)
    const { auth, setTheme, sync } = createHarness()
    const previousLoad = sync.loadSettings()
    await flushPromises()

    auth.setTokens('token-b')
    auth.user = authUser({ userId: 2 })
    await flushPromises()
    nextSettings.resolve(settingsResponse(settingsB))
    await flushPromises()
    previousSettings.resolve(settingsResponse(settingsA))
    await previousLoad
    await flushPromises()

    expect(userApi.getUserSettings).toHaveBeenCalledTimes(2)
    expect(setTheme).toHaveBeenCalledExactlyOnceWith('LIGHT')
    expect(locale.setAppLocale).not.toHaveBeenCalledWith('en', expect.any(Function))
    expect(localStorage.getItem('displayTimeZone')).toBe('Asia/Seoul')
  })

  it('keeps an in-flight settings load through token rotation and same-user profile hydration', async () => {
    const settings = createDeferred<Awaited<ReturnType<typeof userApi.getUserSettings>>>()
    vi.mocked(userApi.getUserSettings).mockReturnValue(settings.promise)
    vi.mocked(authApi.getMe).mockResolvedValue(authUserResponse(authUser({ displayName: 'Updated name' })))
    const { auth, setTheme, sync } = createHarness()
    const pending = sync.loadSettings()
    await flushPromises()

    expect(auth.applyTokenIfCurrent(auth.sessionGeneration, 'token-a', 'rotated-token')).toBe(true)
    await auth.hydrateUser()
    await flushPromises()
    settings.resolve(settingsResponse(settingsA))
    await pending

    expect(userApi.getUserSettings).toHaveBeenCalledOnce()
    expect(setTheme).toHaveBeenCalledExactlyOnceWith('DARK')
    expect(locale.setAppLocale).toHaveBeenCalledExactlyOnceWith('en', expect.any(Function))
    expect(localStorage.getItem('displayTimeZone')).toBe('America/New_York')
  })

  it('rejects a lazy locale commit immediately when user identity changes', async () => {
    const localeLoad = createDeferred<void>()
    let canCommitPreviousLocale: boolean | undefined
    locale.setAppLocale.mockImplementationOnce(async (_language, canCommit: () => boolean) => {
      await localeLoad.promise
      canCommitPreviousLocale = canCommit()
      return true
    })
    vi.mocked(userApi.getUserSettings)
      .mockResolvedValueOnce(settingsResponse(settingsA))
      .mockResolvedValueOnce(settingsResponse(settingsB))
    const { auth, sync } = createHarness()
    const pending = sync.loadSettings()
    await flushPromises()

    // Resolve the locale before Vue's queued watcher runs: the identity guard
    // must reject the old account without relying on a load-generation reset.
    localeLoad.resolve()
    auth.user = authUser({ userId: 2 })
    await pending
    await flushPromises()

    expect(canCommitPreviousLocale).toBe(false)
    expect(locale.setAppLocale).toHaveBeenLastCalledWith('ko', expect.any(Function))
  })
})
