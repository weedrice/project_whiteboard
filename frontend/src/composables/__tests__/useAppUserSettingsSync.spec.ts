import { reactive, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useAppUserSettingsSync } from '../useAppUserSettingsSync'
import type { UserSettings } from '@/types/user'

vi.mock('@/api/user', () => ({
  userApi: {
    getUserSettings: vi.fn(),
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    warn: vi.fn(),
  },
}))

vi.mock('@/utils/storage', () => ({
  Storage: {
    getString: vi.fn(),
  },
}))

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })

  return { promise, reject, resolve }
}

describe('useAppUserSettingsSync', () => {
  it('does not apply settings that resolve after logout', async () => {
    const authStore = reactive({ isAuthenticated: true })
    const setTheme = vi.fn()
    const locale = ref('ko')
    const settingsResult = deferred<UserSettings | null>()
    const queryClient = {
      fetchQuery: vi.fn(() => settingsResult.promise),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      authStore,
      { setTheme },
      queryClient as never,
      locale,
    )

    const pending = sync.loadSettings()
    authStore.isAuthenticated = false
    settingsResult.resolve({
      theme: 'DARK',
      language: 'EN',
    } as UserSettings)
    await pending

    expect(setTheme).not.toHaveBeenCalledWith('DARK')
    expect(locale.value).toBe('ko')
  })

  it('applies settings while still authenticated', async () => {
    const authStore = reactive({ isAuthenticated: true })
    const setTheme = vi.fn()
    const locale = ref('ko')
    const queryClient = {
      fetchQuery: vi.fn().mockResolvedValue({
        theme: 'DARK',
        language: 'EN',
      }),
      removeQueries: vi.fn(),
    }
    const sync = useAppUserSettingsSync(
      authStore,
      { setTheme },
      queryClient as never,
      locale,
    )

    await sync.loadSettings()

    expect(setTheme).toHaveBeenCalledWith('DARK')
    expect(locale.value).toBe('en')
  })
})
