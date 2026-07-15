import { reactive } from 'vue'
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
    const authStore = reactive({ isAuthenticated: true })
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
    expect(localeMocks.setAppLocale).not.toHaveBeenCalled()
  })

  it('applies settings while still authenticated', async () => {
    const authStore = reactive({ isAuthenticated: true })
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
    expect(localeMocks.setAppLocale).toHaveBeenCalledWith('en')
  })

  it('keeps the current locale and logs a warning when lazy loading fails', async () => {
    localeMocks.setAppLocale.mockResolvedValue(false)
    const queryClient = {
      fetchQuery: vi.fn().mockResolvedValue({ theme: 'DARK', language: 'en' }),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      reactive({ isAuthenticated: true }),
      { setTheme: vi.fn() },
      queryClient as never,
    )

    await sync.loadSettings()

    expect(localeMocks.setAppLocale).toHaveBeenCalledWith('en')
    expect(localeMocks.loggerWarn).toHaveBeenCalledWith(
      'Failed to load locale messages during settings sync',
    )
  })
})
