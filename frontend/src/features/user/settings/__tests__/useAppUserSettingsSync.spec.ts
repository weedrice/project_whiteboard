import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAppUserSettingsSync } from '../useAppUserSettingsSync'
import { createDeferred } from '@/test/async'
import type { UserSettings } from '@/types/user'

const localeMocks = vi.hoisted(() => ({
  setAppLocale: vi.fn().mockResolvedValue(true),
  loggerWarn: vi.fn(),
}))

vi.mock('@/i18n', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/i18n')>(),
  setAppLocale: localeMocks.setAppLocale,
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getUserSettings: vi.fn(),
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    warn: localeMocks.loggerWarn,
  },
}))

vi.mock('@/utils/storage', () => ({
  Storage: {
    getString: vi.fn(),
  },
}))

describe('useAppUserSettingsSync', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localeMocks.setAppLocale.mockResolvedValue(true)
  })

  it('does not apply settings that resolve after logout', async () => {
    const authStore = reactive({ isAuthenticated: true, sessionGeneration: 0 })
    const setTheme = vi.fn()
    const settingsResult = createDeferred<UserSettings | null>()
    const queryClient = {
      fetchQuery: vi.fn(() => settingsResult.promise),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      authStore,
      { setTheme },
      queryClient as never,
    )

    const pending = sync.loadSettings()
    authStore.isAuthenticated = false
    settingsResult.resolve({
      theme: 'DARK',
      language: 'en',
      timezone: 'Asia/Seoul',
      hideNsfw: false,
      pushEnabled: false,
    })
    await pending

    expect(setTheme).not.toHaveBeenCalledWith('DARK')
    expect(localeMocks.setAppLocale).toHaveBeenCalledWith('ko', expect.any(Function))
    expect(localeMocks.setAppLocale).not.toHaveBeenCalledWith('en', expect.any(Function))
  })

  it('applies settings while still authenticated', async () => {
    const authStore = reactive({ isAuthenticated: true, sessionGeneration: 0 })
    const setTheme = vi.fn()
    const queryClient = {
      fetchQuery: vi.fn().mockResolvedValue({
        theme: 'DARK',
        language: 'en',
        timezone: 'Asia/Seoul',
        hideNsfw: false,
        pushEnabled: false,
      }),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      authStore,
      { setTheme },
      queryClient as never,
    )

    await sync.loadSettings()

    expect(setTheme).toHaveBeenCalledWith('DARK')
    expect(localeMocks.setAppLocale).toHaveBeenCalledWith('en', expect.any(Function))
  })

  it('ignores account A settings after logout and account B login', async () => {
    const authStore = reactive({ isAuthenticated: true, sessionGeneration: 0 })
    const setTheme = vi.fn()
    const accountASettings = createDeferred<UserSettings | null>()
    const accountBSettings = createDeferred<UserSettings | null>()
    const queryClient = {
      fetchQuery: vi.fn()
        .mockReturnValueOnce(accountASettings.promise)
        .mockReturnValueOnce(accountBSettings.promise),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      authStore,
      { setTheme },
      queryClient as never,
    )

    const pendingAccountA = sync.loadSettings()
    authStore.isAuthenticated = false
    await nextTick()
    authStore.isAuthenticated = true
    await nextTick()

    accountBSettings.resolve({
      theme: 'LIGHT',
      language: 'ko',
      timezone: 'Asia/Seoul',
      hideNsfw: false,
      pushEnabled: false,
    })
    await vi.waitFor(() => {
      expect(setTheme).toHaveBeenCalledWith('LIGHT')
    })

    accountASettings.resolve({
      theme: 'DARK',
      language: 'en',
      timezone: 'Asia/Seoul',
      hideNsfw: false,
      pushEnabled: false,
    })
    await pendingAccountA

    expect(setTheme).not.toHaveBeenCalledWith('DARK')
    expect(localeMocks.setAppLocale).toHaveBeenCalledTimes(3)
    expect(localeMocks.setAppLocale).toHaveBeenCalledWith('ko', expect.any(Function))
    expect(localeMocks.setAppLocale).not.toHaveBeenCalledWith('en', expect.any(Function))
  })

  it('loads the next account settings when generation changes without an auth boolean transition', async () => {
    const authStore = reactive({ isAuthenticated: true, sessionGeneration: 0 })
    const setTheme = vi.fn()
    const queryClient = {
      fetchQuery: vi.fn().mockResolvedValue({
        theme: 'LIGHT',
        language: 'ko',
        timezone: 'Asia/Seoul',
        hideNsfw: false,
        pushEnabled: false,
      }),
      removeQueries: vi.fn(),
    }
    useAppUserSettingsSync(authStore, { setTheme }, queryClient as never)

    authStore.sessionGeneration = 1
    await nextTick()
    await vi.waitFor(() => expect(queryClient.fetchQuery).toHaveBeenCalledTimes(1))

    expect(queryClient.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['session', 1, 'user', 'settings'],
    }))
    expect(setTheme).toHaveBeenCalledWith('LIGHT')
  })

  it('keeps the current locale and logs a warning when lazy loading fails', async () => {
    localeMocks.setAppLocale.mockResolvedValue(false)
    const queryClient = {
      fetchQuery: vi.fn().mockResolvedValue({ theme: 'DARK', language: 'en' }),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      reactive({ isAuthenticated: true, sessionGeneration: 0 }),
      { setTheme: vi.fn() },
      queryClient as never,
    )

    await sync.loadSettings()

    expect(localeMocks.setAppLocale).toHaveBeenCalledWith('en', expect.any(Function))
    expect(localeMocks.loggerWarn).toHaveBeenCalledWith(
      'Failed to load locale messages during settings sync',
    )
  })

  it('passes a commit guard that rejects a locale after the session changes', async () => {
    const authStore = reactive({ isAuthenticated: true, sessionGeneration: 3 })
    const localeLoad = createDeferred<void>()
    let commitAllowed: boolean | undefined
    localeMocks.setAppLocale.mockImplementationOnce(async (_locale, canCommit: () => boolean) => {
      await localeLoad.promise
      commitAllowed = canCommit()
      return true
    })
    const queryClient = {
      fetchQuery: vi.fn().mockResolvedValue({ theme: 'LIGHT', language: 'en' }),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(authStore, { setTheme: vi.fn() }, queryClient as never)

    const pending = sync.loadSettings()
    await vi.waitFor(() => expect(localeMocks.setAppLocale).toHaveBeenCalled())
    authStore.sessionGeneration = 4
    localeLoad.resolve()
    await pending

    expect(commitAllowed).toBe(false)
  })
})
